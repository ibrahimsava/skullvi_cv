package org.example.skulvi_cv.org;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
    @DisplayName("chaque palier payant est plus généreux que le précédent")
    void les_paliers_sont_croissants() {
        Plan[] echelle = { Plan.TRIAL, Plan.STARTER, Plan.PRO, Plan.BUSINESS };

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
    @DisplayName("seul l'essai est gratuit, et seul l'essai refuse le dépassement")
    void l_essai_est_le_seul_plan_sans_depassement() {
        assertThat(Plan.TRIAL.priceCents()).isZero();
        assertThat(Plan.TRIAL.allowsOverage()).isFalse();

        for (Plan plan : Plan.values()) {
            if (plan == Plan.TRIAL) continue;
            assertThat(plan.priceCents()).as("%s doit être payant", plan).isPositive();
            assertThat(plan.allowsOverage()).as("%s doit accepter le dépassement", plan).isTrue();
        }
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
    @DisplayName("un essai échu bloque l'écriture, un abonnement actif ne l'est jamais")
    void etat_d_ecriture_de_l_organisation() {
        Organization essaiEnCours = organisation(Plan.TRIAL, OrgStatus.TRIAL, Instant.now().plus(3, ChronoUnit.DAYS));
        Organization essaiEchu = organisation(Plan.TRIAL, OrgStatus.TRIAL, Instant.now().minus(1, ChronoUnit.DAYS));
        Organization actif = organisation(Plan.PRO, OrgStatus.ACTIVE, null);
        Organization impaye = organisation(Plan.PRO, OrgStatus.PAST_DUE, null);
        Organization suspendu = organisation(Plan.PRO, OrgStatus.SUSPENDED, null);

        assertThat(essaiEnCours.writable()).isTrue();
        assertThat(essaiEchu.writable()).isFalse();
        assertThat(essaiEchu.trialExpired()).isTrue();
        assertThat(actif.writable()).isTrue();
        assertThat(impaye.writable()).isFalse();
        assertThat(suspendu.writable()).isFalse();
    }

    @Test
    @DisplayName("un abonnement payant n'a plus d'échéance d'essai à surveiller")
    void un_plan_paye_ignore_la_date_d_essai() {
        Organization actif = organisation(Plan.PRO, OrgStatus.ACTIVE, Instant.now().minus(30, ChronoUnit.DAYS));

        // Le statut ACTIVE l'emporte : trialExpired() ne vise que les comptes encore en essai.
        assertThat(actif.trialExpired()).isFalse();
        assertThat(actif.writable()).isTrue();
    }

    private static Organization organisation(Plan plan, OrgStatus status, Instant trialEnd) {
        Organization org = new Organization();
        org.setPlan(plan);
        org.setStatus(status);
        org.setTrialEndsAt(trialEnd);
        return org;
    }
}
