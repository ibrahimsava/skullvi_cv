package org.example.skulvi_cv.auth;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.auth.AuthDtos.*;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.roles.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${app.security.jwt-expiration-minutes:60}")
    private long expirationMinutes;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Roles.CANDIDAT); // jamais choisi par le client
        return tokenFor(users.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
        }
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        return toMe(user);
    }

    private TokenResponse tokenFor(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("skulvi")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", expirationMinutes * 60, toMe(user));
    }

    private static MeResponse toMe(AppUser u) {
        return new MeResponse(u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getRole());
    }
}