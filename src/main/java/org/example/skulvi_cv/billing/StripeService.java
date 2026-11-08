package org.example.skulvi_cv.billing;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Passerelle vers le prestataire de paiement.
 *
 * <p>Deux choix structurants :
 *
 * <ul>
 *   <li><b>Passage en caisse hébergé</b> : le client saisit sa carte sur une page du
 *       prestataire, jamais sur la nôtre. Aucune donnée bancaire ne traverse ce serveur, ce
 *       qui nous met hors du périmètre PCI-DSS. Écrire son propre formulaire de carte pour
 *       « maîtriser le design » revient à endosser une obligation de conformité lourde en
 *       échange de presque rien.
 *   <li><b>L'identifiant de l'organisation voyage en métadonnée</b> de l'abonnement. Au retour
 *       du webhook, c'est la seule chose dont on a besoin pour savoir quel compte créditer —
 *       sans jamais faire confiance à un paramètre d'URL.
 * </ul>
 *
 * <p>Le service reste instanciable sans clé : {@link #usable()} répond alors faux et les
 * contrôleurs renvoient une erreur explicite. Une instance de développement doit pouvoir
 * démarrer sans compte de paiement.
 */
@Slf4j
@Service
public class StripeService {

    private final StripeProperties props;
    private final String publicUrl;
    private final StripeClient client;

    public StripeService(StripeProperties props,
                         @Value("${app.public-url:http://localhost:4200}") String publicUrl) {
        this.props = props;
        this.publicUrl = stripTrailingSlash(publicUrl);
        this.client = props.usable() ? StripeClient.builder().setApiKey(props.secretKey()).build() : null;

        if (props.usable()) {
            log.info("Paiement actif ({} tarif(s) configuré(s))", props.prices().size());
        } else {
            log.warn("Paiement inactif : clé ou tarifs absents. Les plans payants ne seront pas "
                    + "souscriptibles en ligne.");
        }
    }

    public boolean usable() {
        return props.usable();
    }

    // ---------------------------------------------------------------- souscription

    /**
     * Ouvre une session de paiement pour un plan.
     *
     * @return l'adresse de la page de paiement, vers laquelle rediriger le navigateur
     */
    public String createCheckoutUrl(Organization org, Plan plan, String ownerEmail) {
        requireUsable();

        String priceId = props.priceFor(plan.priceKey());
        if (priceId == null || priceId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le plan " + plan.label() + " n'est pas encore disponible en ligne. Écrivez-nous.");
        }

        try {
            String customerId = ensureCustomer(org, ownerEmail);

            var params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setSuccessUrl(publicUrl + props.successPath())
                    .setCancelUrl(publicUrl + props.cancelPath())
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    // Posée sur l'abonnement : c'est elle qu'on relit à chaque webhook,
                    // y compris des mois plus tard au renouvellement.
                    .setSubscriptionData(
                            com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                                    .putMetadata("organizationId", org.getId().toString())
                                    .build())
                    .putMetadata("organizationId", org.getId().toString())
                    .setClientReferenceId(org.getId().toString())
                    .build();

            var session = client.checkout().sessions().create(params);
            log.info("Session de paiement {} ouverte pour l'organisation {} (plan {})",
                    session.getId(), org.getId(), plan);
            return session.getUrl();

        } catch (StripeException e) {
            throw unavailable("Ouverture du paiement impossible", e);
        }
    }

    /**
     * Ouvre le portail de gestion de l'abonnement.
     *
     * <p>Changement de carte, téléchargement des factures, résiliation : tout est géré par le
     * prestataire. Réimplémenter ces écrans serait des semaines de travail pour un résultat
     * moins complet, et des factures à produire nous-mêmes.
     */
    public String createPortalUrl(Organization org) {
        requireUsable();

        if (org.getBillingCustomerId() == null) {
            throw ApiException.conflict("Aucun abonnement à gérer pour le moment.");
        }

        try {
            var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(org.getBillingCustomerId())
                    .setReturnUrl(publicUrl + "/admin/abonnement")
                    .build();

            return client.billingPortal().sessions().create(params).getUrl();

        } catch (StripeException e) {
            throw unavailable("Ouverture du portail impossible", e);
        }
    }

    // ---------------------------------------------------------------- webhooks

    /**
     * Vérifie la signature d'un webhook et en extrait l'événement.
     *
     * <p>Contrôle indispensable : l'endpoint est public, et sans cette vérification n'importe
     * qui pourrait envoyer un faux « paiement réussi » et s'offrir le plan Business.
     */
    public Event parseEvent(String payload, String signature) {
        if (props.webhookSecret() == null || props.webhookSecret().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook non configuré");
        }
        try {
            return Webhook.constructEvent(payload, signature, props.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Webhook refusé : signature invalide ({})", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Signature invalide");
        }
    }

    public Subscription retrieveSubscription(String subscriptionId) {
        requireUsable();
        try {
            return client.subscriptions().retrieve(subscriptionId);
        } catch (StripeException e) {
            throw unavailable("Lecture de l'abonnement impossible", e);
        }
    }

    // ---------------------------------------------------------------- interne

    /**
     * Retrouve ou crée le client chez le prestataire.
     *
     * <p>Réutiliser le même identifiant d'un paiement à l'autre évite de créer un doublon à
     * chaque changement de plan — et donc des abonnements orphelins impossibles à rattacher.
     */
    private String ensureCustomer(Organization org, String ownerEmail) throws StripeException {
        if (org.getBillingCustomerId() != null && !org.getBillingCustomerId().isBlank()) {
            return org.getBillingCustomerId();
        }

        var customer = client.customers().create(CustomerCreateParams.builder()
                .setName(org.getName())
                .setEmail(ownerEmail)
                .putAllMetadata(Map.of(
                        "organizationId", org.getId().toString(),
                        "slug", org.getSlug()))
                .build());

        org.setBillingCustomerId(customer.getId());
        return customer.getId();
    }

    private void requireUsable() {
        if (!usable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le paiement en ligne n'est pas encore activé sur cette instance.");
        }
    }

    private ApiException unavailable(String what, StripeException e) {
        log.error("{} : {}", what, e.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                what + ". Réessayez dans un instant.");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
