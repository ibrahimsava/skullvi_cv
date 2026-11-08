package org.example.skulvi_cv.billing;

import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.offer.OfferRepository;
import org.example.skulvi_cv.offer.OfferStatus;
import org.example.skulvi_cv.org.MembershipRepository;
import org.example.skulvi_cv.org.OrgStatus;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.example.skulvi_cv.org.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests des règles de quota.
 *
 * <p>C'est la logique qui décide si un client peut consommer ou non une ressource payante : une
 * erreur ici se traduit soit par du service offert gratuitement, soit par un client qui paie et
 * se retrouve bloqué. Les deux coûtent cher, d'où une couverture détaillée.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaServiceTest {

    private static final UUID ORG = UUID.randomUUID();

    @Mock private OrganizationRepository organizations;
    @Mock private UsageRepository usage;
    @Mock private OfferRepository offers;
    @Mock private MembershipRepository memberships;

    private QuotaService service;
    private Organization org;

    @BeforeEach
    void setUp() {
        service = new QuotaService(organizations, usage, offers, memberships);

        org = new Organization();
        org.setId(ORG);
        org.setName("Cabinet Test");
        org.setSlug("cabinet-test");
        org.setPlan(Plan.FREE);
        org.setStatus(OrgStatus.ACTIVE);

        when(organizations.findById(ORG)).thenReturn(Optional.of(org));
    }

    /** Fixe la consommation d'analyses du mois en cours. */
    private void analysesUsed(int count) {
        UsageRecord record = new UsageRecord();
        record.setOrganizationId(ORG);
        record.setKind(UsageKind.CV_ANALYSIS);
        record.setUsed(count);
        when(usage.findByOrganizationIdAndPeriodAndKind(eq(ORG), any(), eq(UsageKind.CV_ANALYSIS)))
                .thenReturn(Optional.of(record));
    }

    private void onPaidPlan() {
        org.setPlan(Plan.PRO);
        org.setStatus(OrgStatus.ACTIVE);
    }

    @Nested
    @DisplayName("Analyses de CV")
    class Analyses {

        @Test
        void autorise_sous_le_quota() {
            analysesUsed(Plan.FREE.maxAnalysesPerMonth() - 1);
            assertThatCode(() -> service.assertCanAnalyze(ORG)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("le gratuit s'arrête au quota : c'est ce qui le convertit en abonnement")
        void bloque_le_gratuit_au_quota() {
            analysesUsed(Plan.FREE.maxAnalysesPerMonth());

            assertThatThrownBy(() -> service.assertCanAnalyze(ORG))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("analyses gratuites")
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        }

        @Test
        @DisplayName("le message dit quand le compteur repart : sinon le client croit être bloqué")
        void le_message_indique_la_remise_a_zero() {
            analysesUsed(Plan.FREE.maxAnalysesPerMonth());

            assertThatThrownBy(() -> service.assertCanAnalyze(ORG))
                    .hasMessageContaining("repart");
        }

        @Test
        @DisplayName("un plan payant continue au-delà du quota : on ne coupe pas un client en pleine campagne")
        void laisse_passer_le_depassement_sur_un_plan_payant() {
            onPaidPlan();
            analysesUsed(Plan.PRO.maxAnalysesPerMonth() * 3);

            assertThatCode(() -> service.assertCanAnalyze(ORG)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("un impayé n'empêche pas d'analyser : les quotas limitent, pas le statut")
        void un_impaye_ne_bloque_pas() {
            onPaidPlan();
            org.setStatus(OrgStatus.PAST_DUE);
            analysesUsed(0);

            assertThatCode(() -> service.assertCanAnalyze(ORG)).doesNotThrowAnyException();
        }

        @Test
        void une_resiliation_ne_bloque_pas_non_plus() {
            org.setStatus(OrgStatus.CANCELED);
            analysesUsed(0);

            assertThatCode(() -> service.assertCanAnalyze(ORG)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Offres ouvertes")
    class Offres {

        @Test
        void autorise_sous_la_limite() {
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN)).thenReturn(0L);
            assertThatCode(() -> service.assertCanCreateOffer(ORG)).doesNotThrowAnyException();
        }

        @Test
        void bloque_a_la_limite_du_plan() {
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN))
                    .thenReturn((long) Plan.FREE.maxActiveOffers());

            assertThatThrownBy(() -> service.assertCanCreateOffer(ORG))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("offre(s) ouverte(s)");
        }

        @Test
        @DisplayName("seules les offres OUVERTES comptent : clôturer en libère un emplacement")
        void ne_compte_que_les_offres_ouvertes() {
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN)).thenReturn(0L);
            when(offers.countByOrganizationId(ORG)).thenReturn(12L);

            assertThatCode(() -> service.assertCanCreateOffer(ORG)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Sièges")
    class Sieges {

        @Test
        void bloque_a_la_limite_du_plan() {
            when(memberships.countByOrganizationId(ORG))
                    .thenReturn((long) Plan.FREE.maxSeats());

            assertThatThrownBy(() -> service.assertCanAddSeat(ORG))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("utilisateur(s)");
        }

        @Test
        void autorise_sur_un_plan_plus_large() {
            onPaidPlan();
            when(memberships.countByOrganizationId(ORG)).thenReturn(4L);

            assertThatCode(() -> service.assertCanAddSeat(ORG)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Photo de la consommation")
    class Snapshot {

        @Test
        void calcule_le_depassement_et_son_montant() {
            onPaidPlan();
            int limite = Plan.PRO.maxAnalysesPerMonth();
            analysesUsed(limite + 13);
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN)).thenReturn(2L);
            when(memberships.countByOrganizationId(ORG)).thenReturn(2L);

            var snapshot = service.snapshot(ORG);

            assertThat(snapshot.analysesUsed()).isEqualTo(limite + 13);
            assertThat(snapshot.analysesLimit()).isEqualTo(limite);
            assertThat(snapshot.overageCount()).isEqualTo(13);
            assertThat(snapshot.overageCents()).isEqualTo(13 * Plan.OVERAGE_CENTS);
            assertThat(snapshot.paid()).isTrue();
            assertThat(snapshot.writable()).isTrue();
        }

        @Test
        void n_affiche_pas_de_depassement_sous_le_quota() {
            analysesUsed(5);
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN)).thenReturn(1L);
            when(memberships.countByOrganizationId(ORG)).thenReturn(1L);

            var snapshot = service.snapshot(ORG);

            assertThat(snapshot.overageCount()).isZero();
            assertThat(snapshot.overageCents()).isZero();
            assertThat(snapshot.paid()).isFalse();
        }

        @Test
        @DisplayName("une limite illimitée vaut null, pas Integer.MAX_VALUE")
        void traduit_l_illimite_en_null() {
            org.setPlan(Plan.BUSINESS);
            analysesUsed(10);
            when(offers.countByOrganizationIdAndStatus(ORG, OfferStatus.OPEN)).thenReturn(20L);
            when(memberships.countByOrganizationId(ORG)).thenReturn(30L);

            var snapshot = service.snapshot(ORG);

            assertThat(snapshot.activeOffersLimit()).isNull();
            assertThat(snapshot.seatsLimit()).isNull();
            // Le Business plafonne quand même les analyses : cette limite reste un chiffre.
            assertThat(snapshot.analysesLimit()).isEqualTo(Plan.BUSINESS.maxAnalysesPerMonth());
        }
    }

    @Test
    @DisplayName("la grille publique n'est liée à aucune organisation")
    void catalogue_public_sans_organisation() {
        var catalog = service.catalog(null);

        assertThat(catalog.plans()).hasSize(Plan.values().length);
        assertThat(catalog.plans()).noneMatch(p -> p.current());
        assertThat(catalog.plans()).anySatisfy(p -> {
            assertThat(p.plan()).isEqualTo(Plan.FREE);
            assertThat(p.priceCents()).isZero();
            assertThat(p.allowsOverage()).isFalse();
        });
    }

    @Test
    void catalogue_marque_le_plan_en_cours() {
        org.setPlan(Plan.PRO);

        var catalog = service.catalog(ORG);

        assertThat(catalog.plans()).filteredOn(p -> p.current())
                .singleElement()
                .satisfies(p -> assertThat(p.plan()).isEqualTo(Plan.PRO));
    }

    @Test
    void refuse_une_organisation_inconnue() {
        UUID inconnue = UUID.randomUUID();
        when(organizations.findById(inconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertCanAnalyze(inconnue))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
