package org.example.skulvi_cv.offer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.offer.OfferDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse create(@Valid @RequestBody OfferRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<OfferResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public OfferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/close")
    public OfferResponse close(@PathVariable UUID id) {
        return service.close(id);
    }
}
