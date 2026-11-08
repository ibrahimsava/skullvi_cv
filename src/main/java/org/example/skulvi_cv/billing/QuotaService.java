package org.example.skulvi_cv.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.billing.BillingDtos.PlanCatalog;
import org.example.skulvi_cv.billing.BillingDtos.PlanOption;
import org.example.skulvi_cv.billing.BillingDtos.UsageSnapshot;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.offer.OfferRepository;
import org.example.skulvi_cv.offer.OfferStatus;
import org.example.skulvi_cv.org.MembershipRepository;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.example.skulvi_cv.org.Plan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fait respecter les limites du plan et mesure la consommation.
 *
 * <p>Une seule porte d'entrée pour toutes les règles de quota, pour que la question « ce client
 * a-t-il le droit de faire ça ? » ait toujours la même réponse, quel que soit l'appelant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final OrganizationRepository organizations;
    private final UsageRepository usage;
    private final OfferRepository offers;
    private final MembershipRepository memberships;
    private final Clock clock = Clock.systemUTC();

    // ---------------------------------------------------------------- contrôles

    /**
     * Vérifie qu'une analyse de CV peut être consommée.
     *
     * <p>Au-delà du quota, un plan payant continue de fonctionner : le dépassement est facturé
     * à l'analyse. Couper le service d'un client qui paie parce qu'il recrute beaucoup serait
     * absurde. Le plan gratuit, lui, s'arrête net : c'est ce qui le transforme en abonnement.
     */
    @Transactional(readOnly = true)
    public void assertCanAnalyze(UUID orgId) {
        Organization org = require(orgId);
        Plan plan = org.getPlan();

        int used = analysesUsed(orgId);
        if (used < plan.maxAnalysesPerMonth()) {
            return;
        }
        if (!plan.allowsOverage()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Vous avez utilisé vos " + plan.maxAnalysesPerMonth()
                            + " analyses gratuites de ce mois-ci. Le compteur repart le 1er, "
                            + "ou choisissez un abonnement pour continuer tout de suite.");
        }
        log.info("Organisation {} en dépassement : {} analyses ce mois ({} incluses)",
                orgId, used, plan.maxAnalysesPerMonth());
    }

    /** Vérifie qu'une offre supplémentaire peut être ouverte. */
    @Transactional(readOnly = true)
    public void assertCanCreateOffer(UUID orgId) {
        Organization org = require(orgId);

        int limit = org.getPlan().maxActiveOffers();
        long open = offers.countByOrganizationIdAndStatus(orgId, OfferStatus.OPEN);
        if (open >= limit) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Le plan " + org.getPlan().label() + " autorise " + limit
                            + " offre(s) ouverte(s) en même temps. Clôturez une offre pourvue, "
                            + "ou passez à un plan supérieur.");
        }
    }

    /** Vérifie qu'un membre supplémentaire peut être ajouté à l'équipe. */
    @Transactional(readOnly = true)
    public void assertCanAddSeat(UUID orgId) {
        Organization org = require(orgId);

        int limit = org.getPlan().maxSeats();
        long seats = memberships.countByOrganizationId(orgId);
        if (seats >= limit) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Le plan " + org.getPlan().label() + " autorise " + limit
                            + " utilisateur(s). Passez à un plan supérieur pour agrandir l'équipe.");
        }
    }

    // ---------------------------------------------------------------- mesure

    /**
     * Enregistre une analyse consommée.
     *
     * <p>Participe à la transaction de l'appelant, volontairement : la consommation est écrite
     * dans le même mouvement que la tâche mise en file. Si la mise en file échoue, le compteur
     * revient en arrière avec elle — on ne facture pas une analyse qui n'aura jamais lieu.
     */
    @Transactional
    public void recordAnalysis(UUID orgId) {
        usage.recordOne(orgId, currentPeriod(), UsageKind.CV_ANALYSIS.name());
    }

    @Transactional(readOnly = true)
    public int analysesUsed(UUID orgId) {
        return usage.findByOrganizationIdAndPeriodAndKind(orgId, currentPeriod(), UsageKind.CV_ANALYSIS)
                .map(UsageRecord::getUsed)
                .orElse(0);
    }

    // ---------------------------------------------------------------- lecture pour l'UI

    @Transactional(readOnly = true)
    public UsageSnapshot snapshot(UUID orgId) {
        Organization org = require(orgId);
        Plan plan = org.getPlan();

        int used = analysesUsed(orgId);
        int limit = plan.maxAnalysesPerMonth();
        int overage = Math.max(0, used - limit);

        return new UsageSnapshot(
                plan,
                plan.label(),
                org.getStatus(),
                plan.isPaid(),
                org.getCurrentPeriodEnd(),
                org.isCancelAtPeriodEnd(),
                currentPeriod(),
                used,
                capped(limit),
                overage,
                overage * Plan.OVERAGE_CENTS,
                (int) offers.countByOrganizationIdAndStatus(orgId, OfferStatus.OPEN),
                capped(plan.maxActiveOffers()),
                (int) memberships.countByOrganizationId(orgId),
                capped(plan.maxSeats()),
                org.writable());
    }

    @Transactional(readOnly = true)
    public PlanCatalog catalog(UUID orgId) {
        Plan current = orgId == null ? null : require(orgId).getPlan();
        List<PlanOption> options = Arrays.stream(Plan.values())
                .map(p -> new PlanOption(p, p.label(), p.priceCents(), capped(p.maxActiveOffers()),
                        capped(p.maxAnalysesPerMonth()), capped(p.maxSeats()), p.allowsOverage(),
                        Plan.OVERAGE_CENTS, p == current))
                .toList();
        return new PlanCatalog(options);
    }

    /** Traduit la sentinelle « pas de limite » en {@code null} pour l'interface. */
    private static Integer capped(int limit) {
        return limit == Plan.UNLIMITED ? null : limit;
    }

    private Organization require(UUID orgId) {
        return organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + orgId));
    }

    String currentPeriod() {
        return YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();   // AAAA-MM
    }
}
