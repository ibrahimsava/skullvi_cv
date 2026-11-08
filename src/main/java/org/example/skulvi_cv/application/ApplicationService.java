package org.example.skulvi_cv.application;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.analysis.AnalysisQueue;
import org.example.skulvi_cv.analytics.TrackingService;
import org.example.skulvi_cv.analytics.VisitKind;
import org.example.skulvi_cv.analysis.StructuredProfile;
import org.example.skulvi_cv.application.ApplicationDtos.*;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.candidate.Candidate;
import org.example.skulvi_cv.candidate.CandidateRepository;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.common.Json;
import org.example.skulvi_cv.cv.CvStorageService;
import org.example.skulvi_cv.offer.Offer;
import org.example.skulvi_cv.offer.OfferService;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.scoring.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applications;
    private final CandidateRepository candidates;
    private final OfferService offers;
    private final CvStorageService storage;
    private final AppUserRepository users;
    private final QuotaService quotas;
    private final AnalysisQueue queue;
    private final TrackingService tracking;

    // ---------------------------------------------------------------- dépôt

    /**
     * Dépose une candidature et la met en file d'analyse.
     *
     * <p>L'organisation vient de l'offre, jamais de l'appelant : cette route est publique, et un
     * visiteur ne doit avoir aucun moyen d'influencer à quel client sa candidature est rattachée.
     *
     * <p>Le quota est vérifié <b>avant</b> d'accepter le fichier. Refuser tôt évite de stocker un
     * PDF qui ne sera jamais analysé, et laisse au candidat un message clair plutôt qu'une
     * candidature éternellement « en attente ».
     */
    @Transactional
    public Application submit(UUID offerId, ApplicationRequest req, MultipartFile cv, UUID ownerId) {
        Offer offer = offers.getOpenForApplication(offerId);
        Organization org = offer.getOrganization();

        quotas.assertCanAnalyze(org.getId());

        AppUser owner = null;
        if (ownerId != null) {
            owner = users.findById(ownerId).orElseThrow(() -> ApiException.notFound("Compte introuvable"));
            // Connecté : l'email du compte est imposé (on ne postule pas au nom d'un autre)
            req = new ApplicationRequest(req.firstName(), req.lastName(), owner.getEmail(),
                    req.phone(), req.linkedinUrl(), req.githubUrl(), req.portfolioUrl());
        }

        byte[] bytes = readPdf(cv);
        String email = req.email().trim().toLowerCase();

        Candidate candidate = candidates
                .findByOrganizationIdAndEmailIgnoreCase(org.getId(), email)
                .orElseGet(Candidate::new);
        candidate.setOrganization(org);
        candidate.setFirstName(req.firstName());
        candidate.setLastName(req.lastName());
        candidate.setEmail(email);
        candidate.setPhone(req.phone());
        candidate.setLinkedinUrl(req.linkedinUrl());
        candidate.setGithubUrl(req.githubUrl());
        candidate.setPortfolioUrl(req.portfolioUrl());
        candidate = candidates.save(candidate);

        if (applications.existsByOfferIdAndCandidateId(offerId, candidate.getId())) {
            throw ApiException.conflict("Une candidature existe déjà pour cet email et cette offre");
        }

        Application app = new Application();
        app.setOrganization(org);
        app.setOffer(offer);
        app.setCandidate(candidate);
        app.setStatus(ApplicationStatus.CV_UPLOADED);
        app.setCvPath(storage.store(bytes));
        app.setCvHash(sha256(bytes));
        app.setOwner(owner);
        app = applications.save(app);

        queue.enqueue(app, org.getPlan().queuePriority(), true);

        // Fin de l'entonnoir : c'est ce chiffre, rapporté aux vues d'offre, qui dit si
        // la page de candidature fait perdre des candidats en route.
        tracking.recordServerSide(VisitKind.APPLY_DONE, org.getId(), "/postuler/:id");
        return app;
    }

    // ---------------------------------------------------------------- espace recruteur

    @Transactional(readOnly = true)
    public List<ApplicationSummary> list(UUID orgId, UUID offerId) {
        offers.getEntity(orgId, offerId);   // 404 si l'offre n'est pas à cette organisation
        return applications.findByOfferIdAndOrganizationIdOrderBySubmittedAtDesc(offerId, orgId)
                .stream().map(ApplicationService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationSummary get(UUID orgId, UUID id) {
        return toSummary(find(orgId, id));
    }

    @Transactional(readOnly = true)
    public ScoreResponse score(UUID orgId, UUID id) {
        Application app = find(orgId, id);
        if (app.getBreakdownJson() == null) {
            throw ApiException.conflict("Pas encore de score (statut : " + app.getStatus() + ")");
        }
        ScoreResult r = Json.read(app.getBreakdownJson(), ScoreResult.class);
        return new ScoreResponse(app.getId(), name(app), r.total(), r.rawTotal(), r.capped(), r.priority(), r.items());
    }

    /** Relance une analyse en échec. Non facturée : l'échec vient le plus souvent de nous. */
    @Transactional
    public void retry(UUID orgId, UUID id) {
        Application app = find(orgId, id);
        if (app.getStatus() == ApplicationStatus.ANALYZING) {
            throw ApiException.conflict("Une analyse est déjà en cours");
        }
        queue.enqueue(app, app.getOrganization().getPlan().queuePriority(), false);
    }

    @Transactional(readOnly = true)
    public Application findOwnedByOrg(UUID orgId, UUID id) {
        return find(orgId, id);
    }

    // ---------------------------------------------------------------- espace candidat

    @Transactional(readOnly = true)
    public List<MyApplicationSummary> listMine(UUID userId) {
        return applications.findByOwnerIdOrderBySubmittedAtDesc(userId).stream()
                .map(ApplicationService::toMine).toList();
    }

    @Transactional(readOnly = true)
    public MyApplicationSummary getMine(UUID id, UUID userId) {
        return toMine(findOwned(id, userId));
    }

    /**
     * Le profil que l'analyse a extrait du CV le plus récent du candidat.
     *
     * <p>Renvoie un objet « vide mais valide » plutôt qu'un 404 quand aucune analyse n'a encore
     * abouti : côté interface, un état « pas encore disponible » s'affiche proprement, là où une
     * erreur obligerait à traiter le cas normal comme une panne.
     */
    @Transactional(readOnly = true)
    public MyProfile myProfile(UUID userId) {
        List<Application> analyzed = applications.latestAnalyzedForOwner(userId);
        if (analyzed.isEmpty()) {
            return new MyProfile(false, null, null, null);
        }
        Application latest = analyzed.get(0);
        StructuredProfile profile = Json.read(latest.getProfileJson(), StructuredProfile.class);
        return new MyProfile(true, latest.getId(), latest.getSubmittedAt(), profile);
    }

    /** Le candidat met à jour ses coordonnées sur une de ses candidatures. */
    @Transactional
    public MyApplicationSummary updateMyContact(UUID id, UUID userId, ContactUpdateRequest req) {
        Application app = findOwned(id, userId);
        Candidate c = app.getCandidate();
        c.setPhone(trimOrNull(req.phone()));
        c.setLinkedinUrl(trimOrNull(req.linkedinUrl()));
        c.setGithubUrl(trimOrNull(req.githubUrl()));
        c.setPortfolioUrl(trimOrNull(req.portfolioUrl()));
        candidates.save(c);
        return toMine(app);
    }

    /**
     * Le candidat retire sa candidature.
     *
     * <p>Le CV est effacé du disque dans le même mouvement : garder le fichier d'une personne
     * qui a explicitement retiré son dossier n'aurait aucune base légale.
     */
    @Transactional
    public void withdrawMine(UUID id, UUID userId) {
        Application app = findOwned(id, userId);
        storage.delete(app.getCvPath());
        applications.delete(app);
    }

    // ---------------------------------------------------------------- recherche interne

    /** 404 (et non 403) si la candidature appartient à une autre organisation. */
    private Application find(UUID orgId, UUID id) {
        return applications.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Candidature introuvable : " + id));
    }

    /** 404 (et non 403) si la candidature n'appartient pas à l'utilisateur. */
    private Application findOwned(UUID id, UUID userId) {
        return applications.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Candidature introuvable : " + id));
    }

    // ---------------------------------------------------------------- conversions

    public static ApplicationSummary toSummary(Application a) {
        return new ApplicationSummary(a.getId(), name(a), a.getCandidate().getEmail(), a.getStatus(),
                a.getScoreTotal(), a.getPriority(), a.getFailureReason(), a.getSubmittedAt());
    }

    static MyApplicationSummary toMine(Application a) {
        return new MyApplicationSummary(a.getId(), a.getOffer().getId(), a.getOffer().getTitle(),
                a.getOrganization().getName(), a.getStatus(), candidateLabel(a.getStatus()),
                a.getSubmittedAt());
    }

    /**
     * Traduit un statut interne en phrase lisible par le candidat.
     *
     * <p>Les états techniques de l'analyse sont regroupés sous « en cours d'examen » : qu'une
     * extraction de PDF ait échoué chez nous ne concerne pas le candidat, et le lui annoncer
     * l'inquiéterait pour un incident qu'il ne peut pas corriger.
     */
    static String candidateLabel(ApplicationStatus status) {
        return switch (status) {
            case SUBMITTED, CV_UPLOADED -> "Candidature reçue";
            case ANALYZING, ANALYZED, ANALYSIS_FAILED, SCORED -> "En cours d'examen";
            case SHORTLISTED -> "Retenue pour la suite";
            case REJECTED -> "Non retenue";
        };
    }

    private static String name(Application a) {
        return a.getCandidate().getFirstName() + " " + a.getCandidate().getLastName();
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static byte[] readPdf(MultipartFile cv) {
        if (cv == null || cv.isEmpty()) throw ApiException.badRequest("CV manquant");
        try {
            byte[] b = cv.getBytes();
            boolean isPdf = b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            if (!isPdf) throw ApiException.badRequest("Le CV doit être un fichier PDF valide");
            return b;
        } catch (IOException e) {
            throw ApiException.badRequest("Impossible de lire le fichier");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
