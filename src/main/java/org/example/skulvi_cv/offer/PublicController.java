package org.example.skulvi_cv.offer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.billing.BillingDtos.PlanCatalog;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.offer.OfferDtos.PublicOfferResponse;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tout ce qu'un visiteur sans compte peut consulter.
 *
 * <p>Regroupé sous un préfixe {@code /public} à dessein : la règle de sécurité devient lisible
 * d'un coup d'œil dans la configuration, au lieu d'une liste de routes publiques éparpillées
 * qu'on finit par oublier de mettre à jour.
 */
@RestController
@Tag(name = "Public", description = "Offres consultables sans compte")
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final OfferService offers;
    private final OrganizationRepository organizations;
    private final QuotaService quotas;

    /** Fiche publique d'une entreprise : en-tête de sa page carrière. */
    public record PublicOrganization(String name, String slug, int openOffers) {}

    @Operation(summary = "Grille tarifaire publique",
            description = "Alimente la page de tarifs. Aucun plan n'est marqué comme « en cours ».")
    @GetMapping("/plans")
    public PlanCatalog plans() {
        return quotas.catalog(null);
    }

    @Operation(summary = "Toutes les offres ouvertes, toutes entreprises confondues")
    @GetMapping("/offers")
    public List<PublicOfferResponse> offers() {
        return offers.listPublic();
    }

    @Operation(summary = "Détail public d'une offre",
            description = "Sans les critères de notation, qui restent internes au recruteur.")
    @GetMapping("/offers/{id}")
    public PublicOfferResponse offer(@PathVariable UUID id) {
        return offers.getPublic(id);
    }

    @Operation(summary = "Page carrière d'une entreprise")
    @GetMapping("/orgs/{slug}")
    public PublicOrganization organization(@PathVariable String slug) {
        Organization org = organizations.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Entreprise introuvable : " + slug));
        return new PublicOrganization(org.getName(), org.getSlug(), offers.listPublicBySlug(slug).size());
    }

    @Operation(summary = "Offres ouvertes d'une entreprise")
    @GetMapping("/orgs/{slug}/offers")
    public List<PublicOfferResponse> organizationOffers(@PathVariable String slug) {
        return offers.listPublicBySlug(slug);
    }
}
