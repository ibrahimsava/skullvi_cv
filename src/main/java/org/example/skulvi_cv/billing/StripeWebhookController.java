package org.example.skulvi_cv.billing;

import com.stripe.model.Event;
import com.stripe.model.Subscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.Plan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Réception des événements de paiement.
 *
 * <p>C'est ici, et nulle part ailleurs, que l'abonnement d'un client change d'état. Le retour
 * du navigateur après paiement n'est <b>pas</b> une source de vérité : l'utilisateur peut
 * fermer l'onglet avant la redirection, ou forger l'URL de succès. Seul le webhook, signé par
 * le prestataire, fait foi.
 *
 * <p>Trois précautions :
 *
 * <ul>
 *   <li><b>Signature vérifiée</b> avant toute lecture du contenu ;
 *   <li><b>corps brut</b> et non objet désérialisé : la signature porte sur les octets exacts,
 *       un passage par Jackson la casserait ;
 *   <li><b>réponse 200 même sur événement inconnu</b>, sinon le prestataire réessaie en boucle
 *       pendant des jours pour un événement qu'on a simplement choisi d'ignorer.
 * </ul>
 */
@Slf4j
@RestController
@Tag(name = "Webhooks", description = "Événements du prestataire de paiement")
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeService stripe;
    private final SubscriptionService subscriptions;
    private final PriceCatalog prices;

    @Operation(summary = "Événements de paiement",
            description = "Appelé par le prestataire. Signature vérifiée, jamais appelable à la main.")
    @PostMapping("/stripe")
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook sans en-tête de signature : ignoré");
            return ResponseEntity.badRequest().body("signature manquante");
        }

        Event event = stripe.parseEvent(payload, signature);
        log.info("Webhook reçu : {} ({})", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                // Premier paiement validé.
                case "checkout.session.completed" -> onCheckoutCompleted(event);

                // Renouvellement, changement de plan, résiliation programmée, reprise.
                case "customer.subscription.created",
                     "customer.subscription.updated" -> onSubscriptionChanged(event);

                // Fin effective de l'abonnement.
                case "customer.subscription.deleted" -> onSubscriptionEnded(event);

                case "invoice.payment_failed" -> onPaymentFailed(event);

                default -> log.debug("Événement {} non traité", event.getType());
            }
        } catch (RuntimeException e) {
            /*
             * Une erreur de notre côté doit renvoyer un code d'échec : le prestataire
             * réessaiera, et on ne perd pas un paiement à cause d'une base momentanément
             * indisponible.
             */
            log.error("Traitement du webhook {} en échec : {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.status(500).body("erreur de traitement");
        }

        return ResponseEntity.ok("ok");
    }

    // ---------------------------------------------------------------- gestionnaires

    private void onCheckoutCompleted(Event event) {
        var session = (com.stripe.model.checkout.Session) deserialize(event);
        if (session == null) return;

        UUID orgId = orgIdFrom(session.getMetadata(), session.getClientReferenceId());
        if (orgId == null) {
            log.warn("Session {} sans organisation identifiable", session.getId());
            return;
        }

        subscriptions.rememberCustomer(orgId, session.getCustomer());

        if (session.getSubscription() != null) {
            applySubscription(orgId, stripe.retrieveSubscription(session.getSubscription()));
        }
    }

    private void onSubscriptionChanged(Event event) {
        var subscription = (Subscription) deserialize(event);
        if (subscription == null) return;

        resolveOrg(subscription).ifPresent(orgId -> applySubscription(orgId, subscription));
    }

    private void onSubscriptionEnded(Event event) {
        var subscription = (Subscription) deserialize(event);
        if (subscription == null) return;

        resolveOrg(subscription).ifPresent(subscriptions::revertToFree);
    }

    private void onPaymentFailed(Event event) {
        var invoice = (com.stripe.model.Invoice) deserialize(event);
        if (invoice == null || invoice.getCustomer() == null) return;

        subscriptions.findByCustomerId(invoice.getCustomer())
                .map(Organization::getId)
                .ifPresent(subscriptions::markPastDue);
    }

    // ---------------------------------------------------------------- utilitaires

    /**
     * Applique l'état d'un abonnement.
     *
     * <p>Un abonnement « actif » ou « en période d'essai » donne le plan correspondant à son
     * tarif. Tout autre état (impayé, incomplet, résilié) renvoie au gratuit : on ne laisse pas
     * un plan payant actif sur un abonnement qui ne l'est plus.
     */
    private void applySubscription(UUID orgId, Subscription subscription) {
        String status = subscription.getStatus();

        if (!"active".equals(status) && !"trialing".equals(status)) {
            if ("past_due".equals(status) || "unpaid".equals(status)) {
                subscriptions.markPastDue(orgId);
            } else {
                subscriptions.revertToFree(orgId);
            }
            return;
        }

        Plan plan = prices.planFor(priceIdOf(subscription));
        if (plan == null) {
            log.warn("Abonnement {} sur un tarif inconnu : organisation {} laissée en l'état",
                    subscription.getId(), orgId);
            return;
        }

        subscriptions.activate(orgId, plan, subscription.getId(),
                periodEndOf(subscription), Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()));
    }

    /** L'identifiant d'organisation vient des métadonnées, posées à la création de la session. */
    private Optional<UUID> resolveOrg(Subscription subscription) {
        UUID fromMetadata = orgIdFrom(subscription.getMetadata(), null);
        if (fromMetadata != null) {
            return Optional.of(fromMetadata);
        }
        // Repli : un abonnement créé depuis le tableau de bord du prestataire n'a pas de
        // métadonnée. On le rattache alors par l'identifiant client.
        return subscriptions.findByCustomerId(subscription.getCustomer()).map(Organization::getId);
    }

    private static UUID orgIdFrom(java.util.Map<String, String> metadata, String fallback) {
        String raw = metadata == null ? null : metadata.get("organizationId");
        if (raw == null || raw.isBlank()) {
            raw = fallback;
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Identifiant d'organisation illisible dans le webhook : {}", raw);
            return null;
        }
    }

    /** Premier tarif de l'abonnement : nos plans n'ont qu'une ligne. */
    private static String priceIdOf(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData().isEmpty()) {
            return null;
        }
        var price = subscription.getItems().getData().get(0).getPrice();
        return price == null ? null : price.getId();
    }

    /**
     * Fin de la période en cours.
     *
     * <p>Lue sur la ligne d'abonnement : depuis une version récente de l'API, le champ a quitté
     * l'objet abonnement pour ses éléments.
     */
    private static Instant periodEndOf(Subscription subscription) {
        if (subscription.getItems() == null || subscription.getItems().getData().isEmpty()) {
            return null;
        }
        Long end = subscription.getItems().getData().get(0).getCurrentPeriodEnd();
        return end == null ? null : Instant.ofEpochSecond(end);
    }

    private static Object deserialize(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            log.warn("Objet de l'événement {} non désérialisable (version d'API différente)", event.getId());
            return null;
        });
    }
}
