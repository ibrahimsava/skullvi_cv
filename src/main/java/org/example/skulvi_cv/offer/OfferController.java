package org.example.skulvi_cv.offer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.offer.OfferDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Offres", description = "Création, consultation et clôture des offres d'emploi")
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService service;

    @Operation(summary = "Créer une offre")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse create(@Valid @RequestBody OfferRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Lister les offres")
    @GetMapping
    public List<OfferResponse> list() {
        return service.list();
    }

    @Operation(summary = "Détail d'une offre")
    @GetMapping("/{id}")
    public OfferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Clôturer une offre")
    @PostMapping("/{id}/close")
    public OfferResponse close(@PathVariable UUID id) {
        return service.close(id);
    }
}