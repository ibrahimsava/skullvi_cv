package org.example.skulvi_cv.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration du prestataire de paiement.
 *
 * <p>Rien de tout cela n'est écrit dans le code : la clé secrète et le secret de signature
 * des webhooks sont des identifiants de production, et les identifiants de tarif changent
 * entre le mode test et le mode réel.
 *
 * @param enabled         faux par défaut, pour qu'une instance sans clé démarre quand même
 * @param secretKey       clé secrète du compte ({@code sk_test_…} ou {@code sk_live_…})
 * @param webhookSecret   secret de signature de l'endpoint webhook ({@code whsec_…})
 * @param prices          identifiant de tarif par plan, en minuscules : starter, pro, business
 * @param successPath     page de retour après un paiement réussi
 * @param cancelPath      page de retour si le client abandonne
 * @param portalReturnPath page de retour en sortant du portail de facturation
 */
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        boolean enabled,
        String secretKey,
        String webhookSecret,
        Map<String, String> prices,
        String successPath,
        String cancelPath,
        String portalReturnPath) {

    public StripeProperties {
        prices = prices == null ? Map.of() : prices;
        successPath = blankTo(successPath, "/admin/abonnement?paiement=ok");
        cancelPath = blankTo(cancelPath, "/admin/abonnement?paiement=annule");
        portalReturnPath = blankTo(portalReturnPath, "/admin/abonnement");
    }

    /** Le paiement n'est utilisable que si la clé et au moins un tarif sont renseignés. */
    public boolean usable() {
        return enabled && secretKey != null && !secretKey.isBlank() && !prices.isEmpty();
    }

    public String priceFor(String planKey) {
        return prices.get(planKey);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
