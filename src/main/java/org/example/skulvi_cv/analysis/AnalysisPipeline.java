package org.example.skulvi_cv.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.application.Application;
import org.example.skulvi_cv.application.ApplicationRepository;
import org.example.skulvi_cv.application.ApplicationStatus;
import org.example.skulvi_cv.common.Json;
import org.example.skulvi_cv.cv.CvStorageService;
import org.example.skulvi_cv.cv.PdfTextExtractor;
import org.example.skulvi_cv.matching.MatchItem;
import org.example.skulvi_cv.matching.MatchingService;
import org.example.skulvi_cv.scoring.ScoreResult;
import org.example.skulvi_cv.scoring.ScoringService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Pipeline : PDF → texte → profil (IA) → vérification → matching → score.
 *
 * <p>Volontairement non transactionnel : l'appel à l'IA peut durer plus d'une minute, on ne garde
 * pas de transaction ouverte pendant ce temps.
 *
 * <p>L'ordonnancement ne se fait plus ici. C'est {@link AnalysisQueue} qui décide quand et dans
 * quel ordre une analyse part, en fonction du plan de l'organisation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisPipeline {

    private final ApplicationRepository applications;
    private final CvStorageService storage;
    private final PdfTextExtractor pdf;
    private final ProfileExtractor extractor;
    private final ProfileVerifier verifier;
    private final MatchingService matching;
    private final ScoringService scoring;

    /**
     * Analyse une candidature de bout en bout.
     *
     * @return {@code true} si la candidature est passée en {@code SCORED}
     */
    public boolean run(UUID applicationId) {
        Application app = applications.findById(applicationId).orElse(null);
        if (app == null) {
            log.warn("Candidature {} introuvable : analyse abandonnée", applicationId);
            return true;   // rien à retenter, la candidature a été supprimée
        }
        try {
            app.setStatus(ApplicationStatus.ANALYZING);
            app.setFailureReason(null);
            app = applications.save(app);

            String text = pdf.extract(storage.read(app.getCvPath()));
            if (text.isBlank()) throw new IllegalStateException("CV vide ou illisible (PDF scanné ?)");
            app.setExtractedText(text);

            StructuredProfile profile = verifier.verify(extractor.extract(text), text);
            app.setProfileJson(Json.write(profile));
            app.setStatus(ApplicationStatus.ANALYZED);
            app = applications.save(app);

            List<MatchItem> items = matching.match(profile, text, app.getOffer().getCriteria());
            ScoreResult score = scoring.compute(items);
            app.setScoreTotal(score.total());
            app.setPriority(score.priority());
            app.setBreakdownJson(Json.write(score));
            app.setStatus(ApplicationStatus.SCORED);
            applications.save(app);

            log.info("Candidature {} analysée : {}/100 ({})", applicationId, score.total(), score.priority());
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Échec de l'analyse de {} : {}", applicationId, msg);
            app.setStatus(ApplicationStatus.ANALYSIS_FAILED);
            app.setFailureReason(msg.length() > 480 ? msg.substring(0, 480) : msg);
            applications.save(app);
            return false;
        }
    }
}
