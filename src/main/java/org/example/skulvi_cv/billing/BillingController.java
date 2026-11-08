package org.example.skulvi_cv.billing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.auth.AuthService;
import org.example.skulvi_cv.auth.CurrentUser;
import org.example.skulvi_cv.billing.BillingDtos.CheckoutResponse;
import org.example.skulvi_cv.billing.BillingDtos.ChangePlanRequest;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.example.skulvi_cv.org.Plan;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Souscription et gestion de l'abonnement, côté client.
 *
 * <p>Ces routes ne renvoient que des adresses de redirection : tout ce qui touche à la carte
 * bancaire se passe sur les pages du prestataire. L'organisation vient du jeton, jamais d'un
 * paramètre — sinon n'importe qui pourrait payer pour le compte d'un autre, ou plus gênant,
 * ouvrir le portail de facturation d'un autre.
 */
@RestController
@Tag(name = "Abonnement", description = "Souscription et gestion du paiement")
@RequestMapping("/api/v1/org/billing")
@RequiredArgsConstructor
public class BillingController {

    private final StripeService stripe;
    private final OrganizationRepository organizations;
    private final AuthService auth;
    private final CurrentUser current;

    @Operation(summary = "Ouvrir le paiement pour un plan",
            description = "Renvoie l'adresse de la page de paiement hébergée.")
    @PostMapping("/checkout")
    @Transactional
    public CheckoutResponse checkout(@Valid @RequestBody ChangePlanRequest request) {
        current.assertCanManageBilling();

        Plan plan = request.plan();
        if (plan == null || !plan.isPaid()) {
            throw ApiException.badRequest("Choisissez un plan payant.");
        }

        Organization org = organizations.findById(current.orgId())
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable"));

        String email = auth.me(current.id()).email();
        // La transaction reste ouverte : ensureCustomer peut écrire l'identifiant client
        // sur l'organisation, et cette écriture doit être persistée.
        return new CheckoutResponse(stripe.createCheckoutUrl(org, plan, email));
    }

    @Operation(summary = "Ouvrir le portail de facturation",
            description = "Changement de carte, factures, résiliation.")
    @PostMapping("/portal")
    public CheckoutResponse portal() {
        current.assertCanManageBilling();

        Organization org = organizations.findById(current.orgId())
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable"));

        return new CheckoutResponse(stripe.createPortalUrl(org));
    }

    @Operation(summary = "Le paiement en ligne est-il disponible ?",
            description = "Permet à l'interface de proposer un contact plutôt qu'un bouton inopérant.")
    @GetMapping("/status")
    public BillingDtos.BillingStatus status() {
        return new BillingDtos.BillingStatus(stripe.usable());
    }
}
