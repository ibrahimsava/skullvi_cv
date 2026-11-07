package org.example.skulvi_cv.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.skulvi_cv.org.MemberRole;
import org.example.skulvi_cv.org.Plan;
import org.example.skulvi_cv.roles.Roles;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record ProfileUpdateRequest(
            @NotBlank String firstName,
            @NotBlank String lastName) {}

    /**
     * Identité du compte connecté.
     *
     * <p>Les champs d'organisation sont nuls pour un candidat : c'est ce qui permet au front de
     * savoir, sans deviner, s'il doit afficher l'espace recruteur ou l'espace candidat.
     */
    public record MeResponse(UUID id,
                             String email,
                             String firstName,
                             String lastName,
                             Roles role,
                             UUID organizationId,
                             String organizationName,
                             MemberRole orgRole,
                             Plan plan) {}

    public record TokenResponse(String accessToken,
                                String tokenType,
                                long expiresInSeconds,
                                MeResponse user) {}
}
