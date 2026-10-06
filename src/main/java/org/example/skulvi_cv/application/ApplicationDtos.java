package org.example.skulvi_cv.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.skulvi_cv.matching.MatchItem;
import org.example.skulvi_cv.scoring.Priority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public record ApplicationRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            String phone,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl) {}

    public record ApplicationSummary(
            UUID id, String candidateName, String email, ApplicationStatus status,
            Integer score, Priority priority, String failureReason, Instant submittedAt) {}

    public record ScoreResponse(
            UUID applicationId, String candidateName, int total, int rawTotal,
            boolean capped, Priority priority, List<MatchItem> details) {}
}
