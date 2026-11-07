package org.example.skulvi_cv.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

/**
 * Administration de la plateforme, réservée au compte {@code ADMIN}.
 *
 * <p>Sert à encaisser les premiers clients à la main : on reçoit un paiement, on passe
 * l'organisation sur son plan. C'est volontairement manuel pour l'instant — brancher un
 * prestataire de paiement avant d'avoir un client qui paie serait travailler à l'envers, et ces
 * routes sont exactement le point où son webhook viendra se greffer.
 *
 * <p>Le compte de plateforme n'appartient à aucune organisation : il ne peut donc pas lire les
 * candidatures ni les CV d'un client, seulement gérer les abonnements.
 */
@RestController
@Tag(name = "Plateforme", description = "Gestion des abonnements clients (administrateur Skulvi)")
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final OrganizationRepository organizations;
    private final OrganizationService service;
    private final QuotaService quotas;

    /** Une organisation vue depuis la plateforme, avec sa consommation du mois. */
    public record ClientRow(UUID id,
                            String name,
                            String slug,
                            Plan plan,
                            OrgStatus status,
                            Instant trialEndsAt,
                            int analysesUsed,
                            int analysesLimit,
                            int activeOffers,
                            int seats,
                            Instant createdAt) {}

    @Operation(summary = "Lister les organisations clientes")
    @GetMapping("/organizations")
    public List<ClientRow> organizations() {
        return organizations.findAll().stream().map(this::toRow).toList();
    }

    @Operation(summary = "Changer le plan d'une organisation",
            description = "À appeler après encaissement. Bascule aussi le statut en ACTIVE.")
    @PutMapping("/organizations/{id}/plan")
    public OrganizationResponse changePlan(@PathVariable UUID id, @RequestParam Plan plan) {
        return service.changePlan(id, plan);
    }

    @Operation(summary = "Changer le statut d'une organisation",
            description = "Pour suspendre un impayé ou réactiver un compte régularisé.")
    @PutMapping("/organizations/{id}/status")
    public OrganizationResponse changeStatus(@PathVariable UUID id, @RequestParam OrgStatus status) {
        return service.changeStatus(id, status);
    }

    private ClientRow toRow(Organization org) {
        var usage = quotas.snapshot(org.getId());
        return new ClientRow(org.getId(), org.getName(), org.getSlug(), org.getPlan(), org.getStatus(),
                org.getTrialEndsAt(), usage.analysesUsed(), usage.analysesLimit(),
                usage.activeOffers(), usage.seats(), org.getCreatedAt());
    }
}
