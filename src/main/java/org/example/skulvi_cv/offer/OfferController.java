package org.example.skulvi_cv.offer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.auth.CurrentUser;
import org.example.skulvi_cv.offer.OfferDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Gestion des offres par leur propriétaire.
 *
 * <p>Toutes les routes sont authentifiées et filtrées par l'organisation du jeton. La vue
 * publique des offres est ailleurs, dans {@code PublicController} : elle ne doit surtout pas
 * renvoyer la même représentation, qui contient la grille de notation.
 */
@RestController
@Tag(name = "Offres", description = "Création, consultation et clôture des offres (espace recruteur)")
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService service;
    private final CurrentUser current;

    @Operation(summary = "Créer une offre")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse create(@Valid @RequestBody OfferRequest request) {
        current.assertCanWrite();
        return service.create(current.orgId(), request);
    }

    @Operation(summary = "Lister mes offres")
    @GetMapping
    public List<OfferResponse> list() {
        return service.list(current.orgId());
    }

    @Operation(summary = "Détail d'une de mes offres")
    @GetMapping("/{id}")
    public OfferResponse get(@PathVariable UUID id) {
        return service.get(current.orgId(), id);
    }

    @Operation(summary = "Clôturer une offre")
    @PostMapping("/{id}/close")
    public OfferResponse close(@PathVariable UUID id) {
        current.assertCanWrite();
        return service.close(current.orgId(), id);
    }

    @Operation(summary = "Réouvrir une offre",
            description = "Soumis à la limite d'offres ouvertes du plan.")
    @PostMapping("/{id}/reopen")
    public OfferResponse reopen(@PathVariable UUID id) {
        current.assertCanWrite();
        return service.reopen(current.orgId(), id);
    }
}
