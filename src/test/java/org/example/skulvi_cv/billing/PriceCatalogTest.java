package org.example.skulvi_cv.billing;

import org.example.skulvi_cv.org.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correspondance tarif → plan, utilisée par le webhook de paiement.
 *
 * <p>Une erreur ici accorde le mauvais plan à un client qui vient de payer. C'est le genre de
 * bogue qu'on ne découvre qu'au premier encaissement réel.
 */
class PriceCatalogTest {

    private static StripeProperties props(Map<String, String> prices) {
        return new StripeProperties(true, "sk_test_x", "whsec_x", prices, null, null, null);
    }

    @Test
    void retrouve_le_plan_depuis_son_tarif() {
        var catalog = new PriceCatalog(props(Map.of(
                "starter", "price_S",
                "pro", "price_P",
                "business", "price_B")));

        assertThat(catalog.planFor("price_S")).isEqualTo(Plan.STARTER);
        assertThat(catalog.planFor("price_P")).isEqualTo(Plan.PRO);
        assertThat(catalog.planFor("price_B")).isEqualTo(Plan.BUSINESS);
    }

    @Test
    @DisplayName("un tarif inconnu renvoie null : le webhook laisse alors le plan en l'état")
    void tarif_inconnu() {
        var catalog = new PriceCatalog(props(Map.of("pro", "price_P")));

        // Mieux vaut ne rien changer que d'accorder un plan au hasard sur un tarif
        // qu'on ne reconnaît pas.
        assertThat(catalog.planFor("price_inconnu")).isNull();
        assertThat(catalog.planFor(null)).isNull();
    }

    @Test
    void catalogue_vide_quand_rien_n_est_configure() {
        var catalog = new PriceCatalog(props(Map.of()));
        assertThat(catalog.planFor("price_S")).isNull();
    }

    @Test
    @DisplayName("la clé de configuration suit le nom du plan en minuscules")
    void les_cles_correspondent_aux_plans() {
        // Si cette correspondance se décale, la configuration devient muette : aucun tarif
        // ne serait reconnu, et le webhook n'accorderait plus jamais de plan.
        var prices = Map.of(
                Plan.STARTER.priceKey(), "price_S",
                Plan.PRO.priceKey(), "price_P",
                Plan.BUSINESS.priceKey(), "price_B");

        var catalog = new PriceCatalog(props(prices));

        assertThat(catalog.planFor("price_S")).isEqualTo(Plan.STARTER);
        assertThat(catalog.planFor("price_P")).isEqualTo(Plan.PRO);
        assertThat(catalog.planFor("price_B")).isEqualTo(Plan.BUSINESS);
    }

    // ---------------------------------------------------------------- configuration

    @Test
    @DisplayName("le paiement est inutilisable sans clé ou sans tarif")
    void conditions_d_activation() {
        assertThat(props(Map.of("pro", "price_P")).usable()).isTrue();

        // Activé mais sans tarif : un bouton de souscription ne mènerait à rien.
        assertThat(props(Map.of()).usable()).isFalse();

        /*
         * Le cas réel, et celui qui manquait.
         *
         * La configuration déclare les trois clés en dur — « starter: ${STRIPE_PRICE_STARTER:} »
         * — donc la table contient toujours trois entrées, même quand aucune variable n'est
         * renseignée : leurs valeurs sont des chaînes vides. Tester seulement « la table
         * n'est pas vide » répondait donc « paiement disponible » en permanence, et le client
         * recevait une 503 après avoir cliqué sur le bouton de souscription.
         */
        assertThat(props(Map.of("starter", "", "pro", "", "business", "")).usable()).isFalse();
        assertThat(props(Map.of("starter", "   ")).usable()).isFalse();

        // Un seul tarif renseigné parmi les trois suffit : on peut vendre ce plan-là.
        assertThat(props(Map.of("starter", "", "pro", "price_P", "business", "")).usable()).isTrue();

        // Clé absente.
        assertThat(new StripeProperties(true, "", "whsec", Map.of("pro", "p"), null, null, null).usable())

                .isFalse();
        assertThat(new StripeProperties(true, null, "whsec", Map.of("pro", "p"), null, null, null).usable())
                .isFalse();

        // Désactivé explicitement.
        assertThat(new StripeProperties(false, "sk_test", "whsec", Map.of("pro", "p"), null, null, null).usable())
                .isFalse();
    }

    @Test
    @DisplayName("les chemins de retour ont une valeur par défaut")
    void chemins_de_retour_par_defaut() {
        // Sans valeur de repli, une configuration incomplète produirait une URL de retour
        // « https://domaine null » après un paiement réussi.
        var p = new StripeProperties(true, "sk", "wh", Map.of("pro", "price"), null, "  ", null);

        assertThat(p.successPath()).startsWith("/");
        assertThat(p.cancelPath()).startsWith("/");
    }

    @Test
    void les_tarifs_ne_sont_jamais_nuls() {
        var p = new StripeProperties(true, "sk", "wh", null, null, null, null);
        assertThat(p.portalReturnPath()).startsWith("/");
        assertThat(p.prices()).isEmpty();
        assertThat(p.priceFor("pro")).isNull();
    }
}
