package org.example.skulvi_cv.application;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analysis.AnalysisPipeline;
import org.example.skulvi_cv.application.ApplicationDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;
    private final AnalysisPipeline pipeline;

    /** Dépôt d'une candidature + CV (multipart). L'analyse démarre en arrière-plan. */
    @PostMapping(value = "/offers/{offerId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApplicationSummary> submit(@PathVariable UUID offerId,
                                                     @Valid @ModelAttribute ApplicationRequest data,
                                                     @RequestParam("cv") MultipartFile cv) {
        Application app = service.submit(offerId, data, cv);
        pipeline.runAsync(app.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApplicationService.toSummary(app));
    }

    @GetMapping("/offers/{offerId}/applications")
    public List<ApplicationSummary> list(@PathVariable UUID offerId) {
        return service.list(offerId);
    }

    @GetMapping("/applications/{id}")
    public ApplicationSummary get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Relance l'analyse (ex. après un échec de l'IA). */
    @PostMapping("/applications/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void analyze(@PathVariable UUID id) {
        service.assertRetryable(id);
        pipeline.runAsync(id);
    }

    @GetMapping("/applications/{id}/score")
    public ScoreResponse score(@PathVariable UUID id) {
        return service.score(id);
    }
}
