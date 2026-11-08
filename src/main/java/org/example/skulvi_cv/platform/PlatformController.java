package org.example.skulvi_cv.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analytics.AnalyticsDtos.AnalyticsOverview;
import org.example.skulvi_cv.analytics.AnalyticsService;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.org.OrgDtos.OrganizationResponse;
import org.example.skulvi_cv.org.OrgStatus;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.example.skulvi_cv.org.OrganizationService;
import org.example.skulvi_cv.org.Plan;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administration de la plateforme, réservée au compte {@code ADMIN}.
 *
 * <p>Sert à suivre l'activité du service et à régler les cas de facturation traités à la main
 * (virement, geste commercial), en complément du webhook de paiement.
 *
 * <p>Le compte de plateforme n'appartient à aucune organisation : il ne peut donc lire ni les
 * candidatures ni les CV d'un client, seulement des agrégats et les abonnements.
 */
@RestController
@Tag(name = "Plateforme", description = "Suivi de l'activité et des abonnements (administrateur Skulvi)")
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final OrganizationRepository organizations;
    private final OrganizationService service;
    private final QuotaService quotas;
    private final AnalyticsService analytics;

    /** Une organisation cliente, vue depuis la plateforme. */
    public record ClientRow(UUID id,
                            String name,
                            String slug,
                            Plan plan,
                            String planLabel,
                            OrgStatus status,
                            boolean paid,
                            int analysesUsed,
                            Integer analysesLimit,
                            int activeOffers,
                            int seats,
                            Instant createdAt) {}

    @Operation(summary = "Lister les organisations clientes")
    @GetMapping("/organizations")
    public List<ClientRow> organizations() {
        return organizations.findAll().stream().map(this::toRow).toList();
    }

    @Operation(summary = "Fréquentation de tout le site",
            description = "Mêmes indicateurs que pour un client, mais sur l'ensemble du trafic.")
    @GetMapping("/analytics")
    public AnalyticsOverview analytics(@RequestParam(defaultValue = "30") int days) {
        return analytics.overview(null, days);
    }

    @Operation(summary = "Totaux de la plateforme",
            description = "Nombre d'organisations, de comptes, et inscriptions des 30 derniers jours.")
    @GetMapping("/totals")
    public Map<String, Long> totals() {
        return analytics.platformTotals();
    }

    @Operation(summary = "Changer le plan d'une organisation",
            description = """
                    Pour les paiements encaissés hors ligne. Les abonnements souscrits en ligne
                    sont pilotés par le webhook du prestataire, pas par cette route.""")
    @PutMapping("/organizations/{id}/plan")
    public OrganizationResponse changePlan(@PathVariable UUID id, @RequestParam Plan plan) {
        return service.changePlan(id, plan);
    }

    @Operation(summary = "Changer le statut d'une organisation")
    @PutMapping("/organizations/{id}/status")
    public OrganizationResponse changeStatus(@PathVariable UUID id, @RequestParam OrgStatus status) {
        return service.changeStatus(id, status);
    }

    private ClientRow toRow(Organization org) {
        var usage = quotas.snapshot(org.getId());
        return new ClientRow(org.getId(), org.getName(), org.getSlug(), org.getPlan(),
                org.getPlan().label(), org.getStatus(), org.hasPaidPlan(),
                usage.analysesUsed(), usage.analysesLimit(),
                usage.activeOffers(), usage.seats(), org.getCreatedAt());
    }
}
