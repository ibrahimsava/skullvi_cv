package org.example.skulvi_cv.application;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.ApplicationDtos.*;
import org.example.skulvi_cv.Utilisateur.AppUser;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.candidate.Candidate;
import org.example.skulvi_cv.candidate.CandidateRepository;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.common.Json;
import org.example.skulvi_cv.cv.CvStorageService;
import org.example.skulvi_cv.offer.Offer;
import org.example.skulvi_cv.offer.OfferService;
import org.example.skulvi_cv.offer.OfferStatus;
import org.example.skulvi_cv.scoring.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
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

    @Transactional
    public Application submit(UUID offerId, ApplicationRequest req, MultipartFile cv, UUID ownerId) {
        AppUser owner = null;
        if (ownerId != null) {
            owner = users.findById(ownerId).orElseThrow(() -> ApiException.notFound("Compte introuvable"));
            // Connecté : l'email du compte est imposé (on ne postule pas au nom d'un autre)
            req = new ApplicationRequest(req.firstName(), req.lastName(), owner.getEmail(),
                    req.phone(), req.linkedinUrl(), req.githubUrl(), req.portfolioUrl());
        }

        Offer offer = offers.getEntity(offerId);
        boolean expired = offer.getClosingDate() != null && offer.getClosingDate().isBefore(LocalDate.now());
        if (offer.getStatus() != OfferStatus.OPEN || expired) {
            throw ApiException.conflict("Cette offre est fermée");
        }

        byte[] bytes = readPdf(cv);

        Candidate candidate = candidates.findByEmailIgnoreCase(req.email()).orElseGet(Candidate::new);
        candidate.setFirstName(req.firstName());
        candidate.setLastName(req.lastName());
        candidate.setEmail(req.email().trim().toLowerCase());
        candidate.setPhone(req.phone());
        candidate.setLinkedinUrl(req.linkedinUrl());
        candidate.setGithubUrl(req.githubUrl());
        candidate.setPortfolioUrl(req.portfolioUrl());
        candidate = candidates.save(candidate);

        if (applications.existsByOfferIdAndCandidateId(offerId, candidate.getId())) {
            throw ApiException.conflict("Une candidature existe déjà pour cet email et cette offre");
        }

        Application app = new Application();
        app.setOffer(offer);
        app.setCandidate(candidate);
        app.setStatus(ApplicationStatus.CV_UPLOADED);
        app.setCvPath(storage.store(bytes));
        app.setCvHash(sha256(bytes));
        app.setOwner(owner);
        return applications.save(app);
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummary> list(UUID offerId) {
        offers.getEntity(offerId);
        return applications.findByOfferIdOrderBySubmittedAtDesc(offerId).stream().map(ApplicationService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationSummary get(UUID id) {
        return toSummary(find(id));
    }

    @Transactional(readOnly = true)
    public ScoreResponse score(UUID id) {
        Application app = find(id);
        if (app.getBreakdownJson() == null) {
            throw ApiException.conflict("Pas encore de score (statut : " + app.getStatus() + ")");
        }
        ScoreResult r = Json.read(app.getBreakdownJson(), ScoreResult.class);
        return new ScoreResponse(app.getId(), name(app), r.total(), r.rawTotal(), r.capped(), r.priority(), r.items());
    }

    @Transactional(readOnly = true)
    public List<MyApplicationSummary> listMine(UUID userId) {
        return applications.findByOwnerIdOrderBySubmittedAtDesc(userId).stream()
                .map(ApplicationService::toMine).toList();
    }

    @Transactional(readOnly = true)
    public MyApplicationSummary getMine(UUID id, UUID userId) {
        return toMine(findOwned(id, userId));
    }

    @Transactional(readOnly = true)
    public void assertRetryable(UUID id) {
        if (find(id).getStatus() == ApplicationStatus.ANALYZING) {
            throw ApiException.conflict("Une analyse est déjà en cours");
        }
    }

    /** Renvoie 404 (et non 403) si la candidature n'appartient pas à l'utilisateur : on ne révèle pas son existence. */
    private Application findOwned(UUID id, UUID userId) {
        Application app = find(id);
        if (app.getOwner() == null || !app.getOwner().getId().equals(userId)) {
            throw ApiException.notFound("Candidature introuvable : " + id);
        }
        return app;
    }

    private Application find(UUID id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Candidature introuvable : " + id));
    }

    static ApplicationSummary toSummary(Application a) {
        return new ApplicationSummary(a.getId(), name(a), a.getCandidate().getEmail(), a.getStatus(),
                a.getScoreTotal(), a.getPriority(), a.getFailureReason(), a.getSubmittedAt());
    }

    static MyApplicationSummary toMine(Application a) {
        return new MyApplicationSummary(a.getId(), a.getOffer().getId(), a.getOffer().getTitle(),
                a.getStatus(), a.getScoreTotal(), a.getSubmittedAt());
    }

    private static String name(Application a) {
        return a.getCandidate().getFirstName() + " " + a.getCandidate().getLastName();
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