package org.example.skulvi_cv.scoring;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.config.TalentProperties;
import org.example.skulvi_cv.matching.MatchItem;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Score = 100 × (points obtenus / somme des poids de l'offre).
 * Si un critère obligatoire manque, le score est plafonné (talent.scoring.mandatory-missing-cap).
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final TalentProperties props;

    public ScoreResult compute(List<MatchItem> items) {
        double totalWeight = items.stream().mapToDouble(MatchItem::weight).sum();
        if (totalWeight <= 0) {
            throw new IllegalStateException("L'offre n'a aucun critère pondéré");
        }
        double earned = items.stream().mapToDouble(MatchItem::points).sum();
        int raw = (int) Math.round(100.0 * earned / totalWeight);

        boolean mandatoryMissing = items.stream().anyMatch(i -> i.mandatory() && !i.matched());
        int cap = props.scoring().mandatoryMissingCap();
        boolean capped = mandatoryMissing && raw > cap;
        int total = capped ? cap : raw;

        return new ScoreResult(total, raw, capped, priorityOf(total), items);
    }

    public Priority priorityOf(int score) {
        var s = props.scoring();
        if (score >= s.highThreshold()) return Priority.HIGH;
        if (score >= s.reviewThreshold()) return Priority.REVIEW;
        if (score >= s.secondaryThreshold()) return Priority.SECONDARY;
        return Priority.LOW;
    }
}
