package org.example.skulvi_cv.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analysis.AnalysisPipeline;
import org.example.skulvi_cv.application.ApplicationDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Candidatures", description = "Dépôt de candidatures avec CV, analyse et score")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;
    private final AnalysisPipeline pipeline;

    /** Formulaire multipart, utilisé uniquement pour la documentation Swagger. */
    @Schema(name = "ApplicationUploadForm")
    public record ApplicationUploadForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email") String email,
            String phone,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            @Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Fichier CV (PDF, 5 Mo max)") MultipartFile cv) {}

    @Operation(summary = "Déposer une candidature avec CV (multipart)",
            description = "Public. Si un token est fourni, la candidature est rattachée au compte connecté (et l'email du compte est utilisé). L'analyse démarre en arrière-plan.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = ApplicationUploadForm.class)))
    @PostMapping(value = "/offers/{offerId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationSummary> submit(@PathVariable UUID offerId,
                                                     @Parameter(hidden = true) @Valid @ModelAttribute ApplicationRequest data,
                                                     @Parameter(hidden = true) @RequestParam("cv") MultipartFile cv,
                                                     @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        Application app = service.submit(offerId, data, cv, ownerId);
        pipeline.runAsync(app.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApplicationService.toSummary(app));
    }

    @Operation(summary = "Lister les candidatures d'une offre (admin)")
    @GetMapping("/offers/{offerId}/applications")
    public List<ApplicationSummary> list(@PathVariable UUID offerId) {
        return service.list(offerId);
    }

    @Operation(summary = "Détail d'une candidature (admin)")
    @GetMapping("/applications/{id}")
    public ApplicationSummary get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Relancer l'analyse du CV (admin)", description = "Utile après un échec de l'IA.")
    @PostMapping("/applications/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void analyze(@PathVariable UUID id) {
        service.assertRetryable(id);
        pipeline.runAsync(id);
    }

    @Operation(summary = "Score détaillé d'une candidature (admin)")
    @GetMapping("/applications/{id}/score")
    public ScoreResponse score(@PathVariable UUID id) {
        return service.score(id);
    }
}