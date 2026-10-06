package org.example.skulvi_cv.me;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.ApplicationDtos.MyApplicationSummary;
import org.example.skulvi_cv.application.ApplicationService;
import org.example.skulvi_cv.auth.AuthDtos.MeResponse;
import org.example.skulvi_cv.auth.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Mon espace", description = "Espace personnel du candidat connecté")
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthService auth;
    private final ApplicationService applications;

    @Operation(summary = "Mon profil")
    @GetMapping
    public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return auth.me(userId(jwt));
    }

    @Operation(summary = "Mes candidatures")
    @GetMapping("/applications")
    public List<MyApplicationSummary> myApplications(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return applications.listMine(userId(jwt));
    }

    @Operation(summary = "Détail d'une de mes candidatures")
    @GetMapping("/applications/{id}")
    public MyApplicationSummary myApplication(@PathVariable UUID id,
                                              @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return applications.getMine(id, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}