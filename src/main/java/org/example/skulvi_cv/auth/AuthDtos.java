package org.example.skulvi_cv.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public record MeResponse(UUID id, String email, String firstName, String lastName, Roles role) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, MeResponse user) {}
}