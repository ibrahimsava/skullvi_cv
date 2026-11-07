package org.example.skulvi_cv.org;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.OrgDtos.*;
import org.example.skulvi_cv.roles.Roles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Création et administration des organisations : équipe, identité, plan. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** Durée de l'essai gratuit. Assez long pour couvrir un vrai cycle de recrutement. */
    private static final int TRIAL_DAYS = 14;

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final QuotaService quotas;

    // ---------------------------------------------------------------- inscription

    /**
     * Crée en une opération le compte du propriétaire, son organisation et son rattachement.
     *
     * <p>Transactionnel de bout en bout : on ne veut jamais d'un compte sans organisation, qui
     * laisserait l'utilisateur connecté devant une application vide sans pouvoir rien faire.
     *
     * @return le rattachement créé, qui porte tout ce qu'il faut pour émettre le jeton
     */
    @Transactional
    public Membership signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email");
        }

        AppUser owner = new AppUser();
        owner.setEmail(email);
        owner.setFirstName(req.firstName().trim());
        owner.setLastName(req.lastName().trim());
        owner.setPasswordHash(passwordEncoder.encode(req.password()));
        owner.setRole(Roles.RECRUTEUR);
        owner = users.save(owner);

        Organization org = new Organization();
        org.setName(req.organizationName().trim());
        org.setSlug(uniqueSlug(req.organizationName()));
        org.setPlan(Plan.TRIAL);
        org.setStatus(OrgStatus.TRIAL);
        org.setTrialEndsAt(Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS));
        org = organizations.save(org);

        log.info("Nouvelle organisation « {} » ({}) créée par {}", org.getName(), org.getSlug(), email);
        return attach(org, owner, MemberRole.OWNER);
    }

    // ---------------------------------------------------------------- organisation

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID orgId) {
        return toResponse(require(orgId));
    }

    @Transactional
    public OrganizationResponse rename(UUID orgId, OrganizationUpdateRequest req) {
        Organization org = require(orgId);
        org.setName(req.name().trim());
        return toResponse(organizations.save(org));
    }

    /**
     * Change le plan d'une organisation.
     *
     * <p>Réservé à l'administrateur de la plateforme : tant que le prestataire de paiement n'est
     * pas branché, un client ne doit pas pouvoir s'attribuer un plan payant tout seul. C'est le
     * point d'accroche où viendra se poser le webhook de paiement.
     */
    @Transactional
    public OrganizationResponse changePlan(UUID orgId, Plan plan) {
        Organization org = require(orgId);
        Plan previous = org.getPlan();

        org.setPlan(plan);
        if (plan == Plan.TRIAL) {
            org.setStatus(OrgStatus.TRIAL);
            if (org.getTrialEndsAt() == null) {
                org.setTrialEndsAt(Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS));
            }
        } else {
            org.setStatus(OrgStatus.ACTIVE);
            org.setTrialEndsAt(null);   // un abonnement payant n'a plus d'échéance d'essai
        }

        log.info("Organisation {} : plan {} → {}", orgId, previous, plan);
        return toResponse(organizations.save(org));
    }

    @Transactional
    public OrganizationResponse changeStatus(UUID orgId, OrgStatus status) {
        Organization org = require(orgId);
        org.setStatus(status);
        return toResponse(organizations.save(org));
    }

    // ---------------------------------------------------------------- équipe

    @Transactional(readOnly = true)
    public MemberList members(UUID orgId, UUID currentUserId) {
        Organization org = require(orgId);
        List<MemberResponse> list = memberships.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream()
                .map(m -> toMember(m, currentUserId))
                .toList();
        return new MemberList(list, list.size(), org.getPlan().maxSeats());
    }

    /**
     * Ajoute un collègue à l'organisation.
     *
     * <p>Le propriétaire choisit le mot de passe initial et le transmet lui-même : pas d'envoi
     * d'e-mail tant que le service de messagerie n'est pas en place, et donc pas d'invitation
     * silencieusement perdue dans un dossier indésirable.
     */
    @Transactional
    public MemberResponse invite(UUID orgId, InviteRequest req, UUID currentUserId) {
        quotas.assertCanAddSeat(orgId);
        Organization org = require(orgId);

        String email = req.email().trim().toLowerCase();
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            user = new AppUser();
            user.setEmail(email);
            user.setFirstName(req.firstName().trim());
            user.setLastName(req.lastName().trim());
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setRole(Roles.RECRUTEUR);
            user = users.save(user);
        } else if (memberships.existsByOrganizationIdAndUserId(orgId, user.getId())) {
            throw ApiException.conflict("Cette personne fait déjà partie de votre équipe");
        }

        return toMember(attach(org, user, req.role()), currentUserId);
    }

    @Transactional
    public MemberResponse changeRole(UUID orgId, UUID membershipId, MemberRole role, UUID currentUserId) {
        Membership m = requireMember(orgId, membershipId);
        if (m.getRole() == MemberRole.OWNER && role != MemberRole.OWNER) {
            assertNotLastOwner(orgId);
        }
        m.setRole(role);
        return toMember(memberships.save(m), currentUserId);
    }

    @Transactional
    public void remove(UUID orgId, UUID membershipId) {
        Membership m = requireMember(orgId, membershipId);
        if (m.getRole() == MemberRole.OWNER) {
            assertNotLastOwner(orgId);
        }
        memberships.delete(m);
    }

    /**
     * Une organisation sans propriétaire ne serait plus administrable : personne ne pourrait
     * plus gérer l'abonnement ni l'équipe. On refuse de retirer le dernier.
     */
    private void assertNotLastOwner(UUID orgId) {
        long owners = memberships.findByOrganizationIdOrderByCreatedAtAsc(orgId).stream()
                .filter(x -> x.getRole() == MemberRole.OWNER)
                .count();
        if (owners <= 1) {
            throw ApiException.conflict(
                    "Votre organisation doit garder au moins un propriétaire. Nommez-en un autre d'abord.");
        }
    }

    // ---------------------------------------------------------------- utilitaires

    /** Rattachement par défaut d'un compte connecté : sa première organisation. */
    @Transactional(readOnly = true)
    public java.util.Optional<Membership> defaultMembership(UUID userId) {
        return memberships.findByUserIdOrderByCreatedAtAsc(userId).stream().findFirst();
    }

    private Membership attach(Organization org, AppUser user, MemberRole role) {
        Membership m = new Membership();
        m.setOrganization(org);
        m.setUser(user);
        m.setRole(role);
        return memberships.save(m);
    }

    private Organization require(UUID orgId) {
        return organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + orgId));
    }

    /** 404 et non 403 si le rattachement vise une autre organisation : on ne révèle pas son existence. */
    private Membership requireMember(UUID orgId, UUID membershipId) {
        Membership m = memberships.findById(membershipId)
                .orElseThrow(() -> ApiException.notFound("Membre introuvable : " + membershipId));
        if (!m.getOrganization().getId().equals(orgId)) {
            throw ApiException.notFound("Membre introuvable : " + membershipId);
        }
        return m;
    }

    /** Transforme « Cabinet Léon & Fils » en « cabinet-leon-fils », suffixé si déjà pris. */
    private String uniqueSlug(String name) {
        String base = slugify(name);
        if (base.isEmpty()) base = "espace";
        String candidate = base;
        for (int suffix = 2; organizations.existsBySlug(candidate); suffix++) {
            candidate = base + "-" + suffix;
            if (suffix > 200) {
                throw new ApiException(HttpStatus.CONFLICT, "Impossible de générer un identifiant pour ce nom");
            }
        }
        return candidate;
    }

    static String slugify(String input) {
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");                      // retire les accents
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")                  // tout le reste devient un tiret
                .replaceAll("(^-+)|(-+$)", "");                 // pas de tiret en bord
        if (slug.length() <= 50) {
            return slug;
        }
        return slug.substring(0, 50).replaceAll("-+$", "");     // la troncature peut laisser un tiret
    }

    static OrganizationResponse toResponse(Organization o) {
        return new OrganizationResponse(o.getId(), o.getName(), o.getSlug(), o.getPlan(),
                o.getPlan().label(), o.getStatus(), o.getTrialEndsAt(), o.writable(), o.getCreatedAt());
    }

    private static MemberResponse toMember(Membership m, UUID currentUserId) {
        AppUser u = m.getUser();
        return new MemberResponse(m.getId(), u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                m.getRole(), u.getId().equals(currentUserId), m.getCreatedAt());
    }
}
