package org.example.skulvi_cv.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.auth.AuthDtos.*;
import org.example.skulvi_cv.org.OrgDtos.SignupRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Authentification", description = "Inscription candidat, création d'espace recruteur et connexion")
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @Operation(summary = "Créer un compte candidat")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @Operation(summary = "Créer un espace recruteur",
            description = "Crée le compte propriétaire et son organisation, en essai gratuit de 14 jours.")
    @PostMapping("/signup-organization")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signupOrganization(@Valid @RequestBody SignupRequest request) {
        return service.signupOrganization(request);
    }

    @Operation(summary = "Se connecter (renvoie un token JWT)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }
}
