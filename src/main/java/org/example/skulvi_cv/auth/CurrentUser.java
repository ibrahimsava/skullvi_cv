package org.example.skulvi_cv.auth;

import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.MemberRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture de l'utilisateur et de l'organisation portés par le jeton courant.
 *
 * <p>Point unique de vérité du cloisonnement : tous les services passent par ici pour savoir
 * « qui » et « pour quelle organisation ». Aucun identifiant d'organisation ne doit jamais
 * arriver par le corps ou l'URL d'une requête — ce serait exactement la faille qui permet à un
 * client de lire les candidatures d'un autre.
 */
@Component
public class CurrentUser {

    static final String CLAIM_ORG = "org";
    static final String CLAIM_ORG_ROLE = "orgRole";

    /** Identifiant du compte connecté. */
    public UUID id() {
        return UUID.fromString(jwt().getSubject());
    }

    public String email() {
        return jwt().getClaimAsString("email");
    }

    /**
     * Organisation du contexte courant.
     *
     * @throws ApiException 403 si le compte n'appartient à aucune organisation (un candidat, par
     *                      exemple, qui tenterait d'atteindre une route de l'espace recruteur)
     */
    public UUID orgId() {
        return orgIdOrEmpty().orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "Ce compte n'est rattaché à aucune organisation"));
    }

    public Optional<UUID> orgIdOrEmpty() {
        String raw = jwt().getClaimAsString(CLAIM_ORG);
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(raw));
    }

    /** Rôle du compte dans l'organisation courante. */
    public MemberRole orgRole() {
        String raw = jwt().getClaimAsString(CLAIM_ORG_ROLE);
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Ce compte n'est rattaché à aucune organisation");
        }
        return MemberRole.valueOf(raw);
    }

    /** Refuse l'accès si le membre n'a pas le droit d'écrire (rôle VIEWER). */
    public void assertCanWrite() {
        if (!orgRole().canWrite()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Votre rôle ne permet pas cette action");
        }
    }

    /** Refuse l'accès si le membre ne gère pas l'abonnement. */
    public void assertCanManageBilling() {
        if (!orgRole().canManageBilling()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Seul le propriétaire du compte gère l'abonnement");
        }
    }

    /** Refuse l'accès si le membre ne gère pas l'équipe. */
    public void assertCanManageMembers() {
        if (!orgRole().canManageMembers()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Seul le propriétaire du compte gère l'équipe");
        }
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentification requise");
        }
        return token;
    }
    // bj
}
