package org.example.skulvi_cv.org;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cohérence de la grille tarifaire.
 *
 * <p>Ces vérifications paraissent évidentes, et c'est justement pourquoi elles valent la peine :
 * une limite saisie de travers lors d'un ajustement de prix ne provoque aucune erreur visible,
 * elle offre simplement du service gratuitement, ou bloque un client qui a payé.
 */
class PlanTest {

    @ParameterizedTest
    @EnumSource(Plan.class)
    void toute_limite_est_strictement_positive(Plan plan) {
        assertThat(plan.maxAnalysesPerMonth()).isPositive();
        assertThat(plan.maxActiveOffers()).isPositive();
        assertThat(plan.maxSeats()).isPositive();
        assertThat(plan.label()).isNotBlank();
    }

    @Test
    @DisplayName("chaque palier est plus généreux que le précédent")
    void les_paliers_sont_croissants() {
        Plan[] echelle = { Plan.FREE, Plan.STARTER, Plan.PRO, Plan.BUSINESS };

        for (int i = 1; i < echelle.length; i++) {
            Plan bas = echelle[i - 1];
            Plan haut = echelle[i];

            assertThat(haut.priceCents())
                    .as("prix de %s > %s", haut, bas)
                    .isGreaterThan(bas.priceCents());
            assertThat(haut.maxAnalysesPerMonth())
                    .as("analyses de %s >= %s", haut, bas)
                    .isGreaterThanOrEqualTo(bas.maxAnalysesPerMonth());
            assertThat(haut.maxActiveOffers())
                    .as("offres de %s >= %s", haut, bas)
                    .isGreaterThanOrEqualTo(bas.maxActiveOffers());
            assertThat(haut.maxSeats())
                    .as("sièges de %s >= %s", haut, bas)
                    .isGreaterThanOrEqualTo(bas.maxSeats());
            assertThat(haut.queuePriority())
                    .as("priorité de file de %s > %s", haut, bas)
                    .isGreaterThan(bas.queuePriority());
        }
    }

    @Test
    @DisplayName("le gratuit est le seul plan sans prix, et le seul à refuser le dépassement")
    void le_gratuit_est_le_seul_plan_sans_depassement() {
        assertThat(Plan.FREE.priceCents()).isZero();
        assertThat(Plan.FREE.isPaid()).isFalse();
        assertThat(Plan.FREE.allowsOverage()).isFalse();

        for (Plan plan : Plan.values()) {
            if (plan == Plan.FREE) continue;
            assertThat(plan.priceCents()).as("%s doit être payant", plan).isPositive();
            assertThat(plan.isPaid()).as("%s doit être marqué payant", plan).isTrue();
            assertThat(plan.allowsOverage()).as("%s doit accepter le dépassement", plan).isTrue();
        }
    }

    @Test
    @DisplayName("le plan gratuit laisse de quoi faire un vrai recrutement")
    void le_gratuit_est_utilisable() {
        // Un palier gratuit trop étroit ne sert à rien : il faut pouvoir mener une offre
        // jusqu'au bout pour avoir envie de payer la suivante.
        assertThat(Plan.FREE.maxAnalysesPerMonth()).isGreaterThanOrEqualTo(20);
        assertThat(Plan.FREE.maxActiveOffers()).isGreaterThanOrEqualTo(1);
        assertThat(Plan.FREE.maxSeats()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("le prix au dépassement reste supérieur au coût marginal d'une analyse")
    void le_depassement_est_rentable() {
        // Le coût réel d'une analyse est de l'ordre de 1 à 3 centimes de temps CPU.
        assertThat(Plan.OVERAGE_CENTS).isGreaterThan(5);
    }

    @Test
    @DisplayName("le prix par analyse incluse décroît quand on monte en gamme")
    void monter_en_gamme_fait_baisser_le_prix_unitaire() {
        double starter = (double) Plan.STARTER.priceCents() / Plan.STARTER.maxAnalysesPerMonth();
        double pro = (double) Plan.PRO.priceCents() / Plan.PRO.maxAnalysesPerMonth();
        double business = (double) Plan.BUSINESS.priceCents() / Plan.BUSINESS.maxAnalysesPerMonth();

        assertThat(pro).isLessThan(starter);
        assertThat(business).isLessThan(pro);
    }

    @Test
    void business_est_illimite_en_offres_et_en_sieges() {
        assertThat(Plan.BUSINESS.maxActiveOffers()).isEqualTo(Plan.UNLIMITED);
        assertThat(Plan.BUSINESS.maxSeats()).isEqualTo(Plan.UNLIMITED);
    }

    @Test
    @DisplayName("la clé de tarif correspond au nom du plan en minuscules")
    void la_cle_de_tarif_est_stable() {
        // Le webhook retrouve le plan depuis cette clé : la changer casserait la
        // correspondance avec la configuration des tarifs.
        assertThat(Plan.STARTER.priceKey()).isEqualTo("starter");
        assertThat(Plan.PRO.priceKey()).isEqualTo("pro");
        assertThat(Plan.BUSINESS.priceKey()).isEqualTo("business");
    }

    @Test
    @DisplayName("aucun statut ne coupe l'accès : le gratuit reste toujours utilisable")
    void l_acces_n_est_jamais_coupe() {
        for (OrgStatus status : OrgStatus.values()) {
            Organization org = organisation(Plan.FREE, status);
            assertThat(org.writable())
                    .as("une organisation en %s doit rester utilisable", status)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("seul l'impayé demande une intervention")
    void seul_l_impaye_est_signale() {
        assertThat(OrgStatus.ACTIVE.needsAttention()).isFalse();
        assertThat(OrgStatus.CANCELED.needsAttention()).isFalse();
        assertThat(OrgStatus.PAST_DUE.needsAttention()).isTrue();
    }

    @Test
    void le_plan_paye_est_reconnu_sur_l_organisation() {
        assertThat(organisation(Plan.FREE, OrgStatus.ACTIVE).hasPaidPlan()).isFalse();
        assertThat(organisation(Plan.PRO, OrgStatus.ACTIVE).hasPaidPlan()).isTrue();
    }

    private static Organization organisation(Plan plan, OrgStatus status) {
        Organization org = new Organization();
        org.setPlan(plan);
        org.setStatus(status);
        return org;
    }
}
