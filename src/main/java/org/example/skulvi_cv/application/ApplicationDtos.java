package org.example.skulvi_cv.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.skulvi_cv.analysis.StructuredProfile;
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

    /**
     * Vue réservée au candidat connecté.
     *
     * <p>Ni le score ni la priorité n'y figurent, et c'est un choix assumé : un chiffre sur 100
     * calculé par une machine, montré sans le contexte de l'offre ni l'avis du recruteur, est à
     * la fois blessant et trompeur. Le candidat voit où en est son dossier, pas comment il a été
     * noté. La décision reste humaine, et c'est le recruteur qui la communique.
     */
    public record MyApplicationSummary(
            UUID id,
            UUID offerId,
            String offerTitle,
            String organizationName,
            ApplicationStatus status,
            String statusLabel,
            Instant submittedAt) {}

    /**
     * Ce que l'analyse a lu dans le CV du candidat, tel qu'il peut le consulter.
     *
     * <p>C'est l'exercice du droit d'accès du RGPD, et c'est aussi utile : voir noir sur blanc
     * les compétences que la machine a su extraire de son CV dit immédiatement au candidat ce
     * qui est mal rédigé ou illisible dans son document.
     */
    public record MyProfile(
            boolean available,
            UUID applicationId,
            Instant analyzedAt,
            StructuredProfile profile) {}

    public record ScoreResponse(
            UUID applicationId, String candidateName, int total, int rawTotal,
            boolean capped, Priority priority, List<MatchItem> details) {}

    /** Mise à jour des coordonnées par le candidat lui-même. */
    public record ContactUpdateRequest(
            String phone,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl) {}
}
