package org.example.skulvi_cv.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.ApplicationDtos.*;
import org.example.skulvi_cv.auth.CurrentUser;
import org.example.skulvi_cv.common.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Candidatures", description = "Dépôt de candidatures avec CV, analyse et score")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    /** Plafond par adresse IP sur la route publique de dépôt. */
    private static final int MAX_APPLICATIONS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ApplicationService service;
    private final CurrentUser current;
    private final RateLimiter rateLimiter;

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

    // ---------------------------------------------------------------- public

    @Operation(summary = "Déposer une candidature avec CV (multipart)",
            description = """
                    Public, avec une limite de 5 dépôts par tranche de 10 minutes et par adresse IP.
                    Si un token est fourni, la candidature est rattachée au compte connecté
                    (et l'email du compte est utilisé). L'analyse est mise en file d'attente.""")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = ApplicationUploadForm.class)))
    @PostMapping(value = "/offers/{offerId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationSummary> submit(@PathVariable UUID offerId,
                                                     @Parameter(hidden = true) @Valid @ModelAttribute ApplicationRequest data,
                                                     @Parameter(hidden = true) @RequestParam("cv") MultipartFile cv,
                                                     @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                     @Parameter(hidden = true) HttpServletRequest http) {
        rateLimiter.check("apply:" + clientIp(http), MAX_APPLICATIONS, WINDOW);

        UUID ownerId = jwt == null ? null : UUID.fromString(jwt.getSubject());
        Application app = service.submit(offerId, data, cv, ownerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApplicationService.toSummary(app));
    }

    // ---------------------------------------------------------------- espace recruteur

    @Operation(summary = "Lister les candidatures d'une offre")
    @GetMapping("/offers/{offerId}/applications")
    public List<ApplicationSummary> list(@PathVariable UUID offerId) {
        return service.list(current.orgId(), offerId);
    }

    @Operation(summary = "Détail d'une candidature")
    @GetMapping("/applications/{id}")
    public ApplicationSummary get(@PathVariable UUID id) {
        return service.get(current.orgId(), id);
    }

    @Operation(summary = "Relancer l'analyse du CV",
            description = "Utile après un échec de l'IA. Cette relance n'est pas décomptée du quota.")
    @PostMapping("/applications/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void analyze(@PathVariable UUID id) {
        current.assertCanWrite();
        service.retry(current.orgId(), id);
    }

    @Operation(summary = "Score détaillé d'une candidature")
    @GetMapping("/applications/{id}/score")
    public ScoreResponse score(@PathVariable UUID id) {
        return service.score(current.orgId(), id);
    }

    /**
     * Adresse du client.
     *
     * <p>Le front est servi par un reverse proxy, donc {@code getRemoteAddr()} renverrait
     * l'adresse du proxy et la limite s'appliquerait à tous les visiteurs ensemble. On lit donc
     * {@code X-Forwarded-For}, dont la première valeur est celle du client d'origine.
     */
    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
