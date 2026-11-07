package org.example.skulvi_cv.billing;

import org.example.skulvi_cv.org.OrgStatus;
import org.example.skulvi_cv.org.Plan;

import java.time.Instant;
import java.util.List;

public final class BillingDtos {

    private BillingDtos() {}

    /**
     * Ce que l'organisation consomme ce mois-ci, face à ce que son plan autorise.
     * Alimente directement la page « Abonnement » : aucun calcul de quota côté front.
     *
     * <p>Les limites sont des {@code Integer} et valent {@code null} quand le plan ne plafonne
     * pas. Transmettre la sentinelle {@code Integer.MAX_VALUE} telle quelle afficherait
     * « 2147483647 offres » à l'écran et obligerait le front à connaître cette convention.
     */
    public record UsageSnapshot(
            Plan plan,
            String planLabel,
            OrgStatus status,
            Instant trialEndsAt,
            Integer trialDaysLeft,
            String period,
            int analysesUsed,
            Integer analysesLimit,
            int overageCount,
            int overageCents,
            int activeOffers,
            Integer activeOffersLimit,
            int seats,
            Integer seatsLimit,
            boolean writable) {}

    /** Une ligne de la grille tarifaire, pour la page publique et la page d'abonnement. */
    public record PlanOption(
            Plan plan,
            String label,
            int priceCents,
            Integer maxActiveOffers,
            Integer maxAnalysesPerMonth,
            Integer maxSeats,
            boolean allowsOverage,
            int overageCents,
            boolean current) {}

    public record PlanCatalog(List<PlanOption> plans) {}

    /** Demande de changement de plan. Le paiement réel viendra du prestataire. */
    public record ChangePlanRequest(Plan plan) {}
}
