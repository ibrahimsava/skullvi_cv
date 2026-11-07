package org.example.skulvi_cv.candidate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.ApplicationDtos.ContactUpdateRequest;
import org.example.skulvi_cv.application.ApplicationDtos.MyApplicationSummary;
import org.example.skulvi_cv.application.ApplicationDtos.MyProfile;
import org.example.skulvi_cv.application.ApplicationService;
import org.example.skulvi_cv.auth.AuthDtos.MeResponse;
import org.example.skulvi_cv.auth.AuthDtos.ProfileUpdateRequest;
import org.example.skulvi_cv.auth.AuthService;
import org.example.skulvi_cv.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Espace personnel, accessible à tout compte connecté.
 *
 * <p>Sert aussi bien un candidat qu'un recruteur : chacun y voit son identité. Les routes de
 * candidature ne renvoient que ce dont l'utilisateur est le déposant, filtré sur l'identifiant
 * du jeton — jamais sur un paramètre d'URL.
 */
@RestController
@Tag(name = "Mon espace", description = "Profil et candidatures du compte connecté")
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService auth;
    private final ApplicationService applications;
    private final CurrentUser current;

    // ---------------------------------------------------------------- identité

    @Operation(summary = "Mon profil")
    @GetMapping
    public MeResponse me() {
        return auth.me(current.id());
    }

    @Operation(summary = "Modifier mon nom")
    @PutMapping
    public MeResponse updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return auth.updateProfile(current.id(), request);
    }

    // ---------------------------------------------------------------- candidatures

    @Operation(summary = "Mes candidatures")
    @GetMapping("/applications")
    public List<MyApplicationSummary> myApplications() {
        return applications.listMine(current.id());
    }

    @Operation(summary = "Détail d'une de mes candidatures")
    @GetMapping("/applications/{id}")
    public MyApplicationSummary myApplication(@PathVariable UUID id) {
        return applications.getMine(id, current.id());
    }

    @Operation(summary = "Mettre à jour mes coordonnées sur une candidature")
    @PutMapping("/applications/{id}/contact")
    public MyApplicationSummary updateContact(@PathVariable UUID id,
                                              @Valid @RequestBody ContactUpdateRequest request) {
        return applications.updateMyContact(id, current.id(), request);
    }

    @Operation(summary = "Retirer une de mes candidatures",
            description = "Supprime la candidature et le CV associé.")
    @DeleteMapping("/applications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id) {
        applications.withdrawMine(id, current.id());
    }

    // ---------------------------------------------------------------- profil extrait

    @Operation(summary = "Ce que l'analyse a lu dans mon CV",
            description = """
                    Profil structuré issu de la dernière candidature analysée : compétences,
                    expériences, formations. Permet au candidat de vérifier ce que la machine a
                    su extraire de son document, et de corriger un CV mal lu.""")
    @GetMapping("/profile")
    public MyProfile myProfile() {
        return applications.myProfile(current.id());
    }
}
