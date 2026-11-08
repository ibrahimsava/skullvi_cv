package org.example.skulvi_cv.auth;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.analytics.TrackingService;
import org.example.skulvi_cv.analytics.VisitKind;
import org.example.skulvi_cv.auth.AuthDtos.*;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.Membership;
import org.example.skulvi_cv.org.OrgDtos.SignupRequest;
import org.example.skulvi_cv.org.OrganizationService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final OrganizationService organizations;
    private final TrackingService tracking;

    @Value("${app.security.jwt-expiration-minutes:60}")
    private long expirationMinutes;

    /** Inscription d'un candidat : un compte, aucune organisation. */
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
        user.setRole(Roles.CANDIDAT);   // jamais choisi par le client
        AppUser saved = users.save(user);

        tracking.recordServerSide(VisitKind.SIGNUP_CANDIDATE, null, "/register");
        return tokenFor(saved, null);
    }

    /** Inscription d'un recruteur : son compte et son organisation, en période d'essai. */
    @Transactional
    public TokenResponse signupOrganization(SignupRequest req) {
        Membership membership = organizations.signup(req);

        tracking.recordServerSide(VisitKind.SIGNUP_ORG,
                membership.getOrganization().getId(), "/inscription-entreprise");
        return tokenFor(membership.getUser(), membership);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
        }
        return tokenFor(user, organizations.defaultMembership(user.getId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        AppUser user = require(userId);
        return toMe(user, organizations.defaultMembership(userId).orElse(null));
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, ProfileUpdateRequest req) {
        AppUser user = require(userId);
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        return toMe(users.save(user), organizations.defaultMembership(userId).orElse(null));
    }

    private AppUser require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Compte introuvable"));
    }

    /**
     * Émet le jeton.
     *
     * <p>L'organisation est inscrite <b>dans le jeton</b> et nulle part ailleurs : le serveur n'a
     * donc jamais besoin de faire confiance à un identifiant d'organisation envoyé par le client.
     * C'est ce qui garantit qu'un recruteur ne peut pas lire les candidatures d'un autre en
     * modifiant une URL.
     */
    private TokenResponse tokenFor(AppUser user, Membership membership) {
        Instant now = Instant.now();

        List<String> roles = new ArrayList<>();
        roles.add(user.getRole().name());
        if (membership != null) {
            roles.add(membership.getRole().name());
        }

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("skulvi")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles);

        if (membership != null) {
            claims.claim(CurrentUser.CLAIM_ORG, membership.getOrganization().getId().toString());
            claims.claim(CurrentUser.CLAIM_ORG_ROLE, membership.getRole().name());
        }

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", expirationMinutes * 60, toMe(user, membership));
    }

    private static MeResponse toMe(AppUser u, Membership m) {
        return new MeResponse(u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getRole(),
                m == null ? null : m.getOrganization().getId(),
                m == null ? null : m.getOrganization().getName(),
                m == null ? null : m.getRole(),
                m == null ? null : m.getOrganization().getPlan());
    }
}
