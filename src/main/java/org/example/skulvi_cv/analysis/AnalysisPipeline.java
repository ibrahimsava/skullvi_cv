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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Pipeline : PDF → texte → profil (IA) → vérification → matching → score.
 * Volontairement non transactionnel : l'appel à l'IA peut durer, on ne garde pas de transaction ouverte.
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

    @Async
    public void runAsync(UUID applicationId) {
        run(applicationId);
    }

    public void run(UUID applicationId) {
        Application app = applications.findById(applicationId).orElse(null);
        if (app == null) return;
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
        } catch (Exception e) {
            log.warn("Échec de l'analyse de {} : {}", applicationId, e.getMessage());
            app.setStatus(ApplicationStatus.ANALYSIS_FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            app.setFailureReason(msg.length() > 480 ? msg.substring(0, 480) : msg);
            applications.save(app);
        }
    }
}
