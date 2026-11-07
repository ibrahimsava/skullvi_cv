package org.example.skulvi_cv.org;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.auth.CurrentUser;
import org.example.skulvi_cv.billing.BillingDtos.PlanCatalog;
import org.example.skulvi_cv.billing.BillingDtos.UsageSnapshot;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.org.OrgDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Administration de son propre espace : identité, équipe, consommation.
 *
 * <p>Aucune route ne prend d'identifiant d'organisation en paramètre : il vient du jeton. Un
 * propriétaire ne peut donc pas administrer l'organisation d'un autre, même en forgeant l'URL.
 */
@RestController
@Tag(name = "Mon organisation", description = "Équipe, abonnement et consommation")
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService service;
    private final QuotaService quotas;
    private final CurrentUser current;

    // ---------------------------------------------------------------- identité

    @Operation(summary = "Mon organisation")
    @GetMapping
    public OrganizationResponse get() {
        return service.get(current.orgId());
    }

    @Operation(summary = "Renommer mon organisation")
    @PutMapping
    public OrganizationResponse rename(@Valid @RequestBody OrganizationUpdateRequest request) {
        current.assertCanManageBilling();
        return service.rename(current.orgId(), request);
    }

    // ---------------------------------------------------------------- consommation

    @Operation(summary = "Ma consommation du mois",
            description = "Analyses consommées, offres ouvertes, sièges utilisés, face aux limites du plan.")
    @GetMapping("/usage")
    public UsageSnapshot usage() {
        return quotas.snapshot(current.orgId());
    }

    @Operation(summary = "Grille tarifaire",
            description = "Les plans disponibles, avec celui en cours marqué.")
    @GetMapping("/plans")
    public PlanCatalog plans() {
        return quotas.catalog(current.orgId());
    }

    // ---------------------------------------------------------------- équipe

    @Operation(summary = "Les membres de mon équipe")
    @GetMapping("/members")
    public MemberList members() {
        return service.members(current.orgId(), current.id());
    }

    @Operation(summary = "Ajouter un membre",
            description = "Soumis à la limite de sièges du plan. Le mot de passe initial est transmis par le propriétaire.")
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse invite(@Valid @RequestBody InviteRequest request) {
        current.assertCanManageMembers();
        return service.invite(current.orgId(), request, current.id());
    }

    @Operation(summary = "Changer le rôle d'un membre")
    @PutMapping("/members/{membershipId}/role")
    public MemberResponse changeRole(@PathVariable UUID membershipId,
                                     @Valid @RequestBody RoleUpdateRequest request) {
        current.assertCanManageMembers();
        return service.changeRole(current.orgId(), membershipId, request.role(), current.id());
    }

    @Operation(summary = "Retirer un membre de l'équipe",
            description = "Le compte n'est pas supprimé, il est seulement détaché de l'organisation.")
    @DeleteMapping("/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID membershipId) {
        current.assertCanManageMembers();
        service.remove(current.orgId(), membershipId);
    }
}
