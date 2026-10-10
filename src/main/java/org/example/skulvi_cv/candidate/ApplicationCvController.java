package org.example.skulvi_cv.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@Tag(name = "Candidatures", description = "CV et suppression (admin)")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationCvController {

    private final ApplicationRepository repository;

    @Value("${CV_STORAGE_DIR:/data/cvs}")
    private String cvStorageDir;

    @Operation(summary = "Voir ou télécharger le CV d'une candidature (admin)")
    @GetMapping("/applications/{id}/cv")
    public ResponseEntity<Resource> getCv(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        Application app = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));

        Path file = resolveFile(app);
        if (file == null || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier CV introuvable");
        }

        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("cv-" + app.getId() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(file));
    }

    @Operation(summary = "Supprimer une candidature et son CV (admin)")
    @DeleteMapping("/applications/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException {
        Application app = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));

        Path file = resolveFile(app);
        repository.delete(app);
        if (file != null) {
            Files.deleteIfExists(file);
        }
        return ResponseEntity.noContent().build();
    }

    /** Fonctionne que cvPath soit un chemin complet ou juste un nom de fichier. */
    private Path resolveFile(Application app) {
        if (app.getCvPath() == null || app.getCvPath().isBlank()) return null;
        Path baseDir = Paths.get(cvStorageDir).toAbsolutePath().normalize();
        Path file = baseDir.resolve(app.getCvPath()).normalize();
        return file.startsWith(baseDir) ? file : null;   // empêche de sortir du dossier
    }
}