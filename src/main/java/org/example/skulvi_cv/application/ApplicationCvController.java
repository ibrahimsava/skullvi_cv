package org.example.skulvi_cv.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.auth.CurrentUser;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.cv.CvStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Consultation et suppression du CV d'une candidature, côté recruteur.
 *
 * <p>Chaque accès est filtré par l'organisation du jeton. Auparavant un simple
 * {@code findById} suffisait : n'importe quel recruteur authentifié pouvait télécharger le CV
 * d'une candidature d'un autre client s'il en connaissait l'identifiant.
 */
@RestController
@Tag(name = "Candidatures", description = "CV et suppression (espace recruteur)")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationCvController {

    private final ApplicationRepository repository;
    private final ApplicationService service;
    private final CvStorageService storage;
    private final CurrentUser current;

    @Operation(summary = "Voir ou télécharger le CV d'une candidature")
    @GetMapping("/applications/{id}/cv")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getCv(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        Application app = service.findOwnedByOrg(current.orgId(), id);

        Path file = storage.locate(app.getCvPath());
        if (file == null) {
            throw ApiException.notFound("Fichier CV introuvable");
        }

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("cv-" + app.getId() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file));
    }

    @Operation(summary = "Supprimer une candidature et son CV")
    @DeleteMapping("/applications/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        current.assertCanWrite();
        Application app = service.findOwnedByOrg(current.orgId(), id);

        String cvPath = app.getCvPath();
        repository.delete(app);
        storage.delete(cvPath);
        return ResponseEntity.noContent().build();
    }
}
