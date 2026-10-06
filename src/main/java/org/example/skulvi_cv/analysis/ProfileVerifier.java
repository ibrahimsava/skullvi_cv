package org.example.skulvi_cv.analysis;

import org.example.skulvi_cv.common.TextUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/** Garde-fou anti-hallucination : une compétence n'est retenue que si le CV la justifie réellement. */
@Component
public class ProfileVerifier {

    public StructuredProfile verify(StructuredProfile p, String cvText) {
        String folded = TextUtils.fold(cvText).replaceAll("\\s+", " ");
        List<StructuredProfile.Skill> kept = p.skills().stream()
                .filter(s -> s.name() != null && !s.name().isBlank())
                .filter(s -> isJustified(s, folded, cvText))
                .toList();
        return new StructuredProfile(kept, p.education(), p.experience(), p.projects(),
                p.languages(), p.certifications(), p.totalExperienceYears());
    }

    private boolean isJustified(StructuredProfile.Skill s, String foldedText, String rawText) {
        String evidence = TextUtils.fold(s.evidence()).replaceAll("\\s+", " ").strip();
        if (evidence.length() >= 3 && foldedText.contains(evidence)) return true;
        return TextUtils.containsWord(rawText, s.name());
    }
}
