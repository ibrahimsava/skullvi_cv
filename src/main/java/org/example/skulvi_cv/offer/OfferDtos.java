package org.example.skulvi_cv.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OfferDtos {

    private OfferDtos() {}

    public record CriterionRequest(
            @NotBlank String name,
            @NotNull CriterionType type,
            boolean mandatory,
            @Min(1) @Max(100) int weight,
            Integer threshold) {}

    public record OfferRequest(
            @NotBlank String title,
            String description,
            String domain,
            String level,
            Integer minExperienceYears,
            LocalDate startDate,
            LocalDate closingDate,
            @NotEmpty @Valid List<CriterionRequest> criteria) {}

    public record CriterionResponse(UUID id,
                                    String name,
                                    CriterionType type,
                                    boolean mandatory,
                                    int weight,
                                    Integer threshold) {}

    public record OfferResponse(
            UUID id,
            String title,
            String description,
            String domain,
            String level,
            Integer minExperienceYears,
            LocalDate startDate,
            LocalDate closingDate,
            OfferStatus status, List<CriterionResponse> criteria) {}
}
