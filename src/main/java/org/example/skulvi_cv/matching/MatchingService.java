package org.example.skulvi_cv.matching;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analysis.StructuredProfile;
import org.example.skulvi_cv.common.TextUtils;
import org.example.skulvi_cv.offer.OfferCriterion;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Compare le profil du candidat aux critères de l'offre. Déterministe et testable : aucune IA ici. */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final SkillNormalizer normalizer;

    public List<MatchItem> match(StructuredProfile profile, String cvText, List<OfferCriterion> criteria) {
        return criteria.stream().map(c -> switch (c.getType()) {
            case SKILL -> matchSkill(c, profile, cvText);
            case EXPERIENCE -> matchExperience(c, profile);
            case EDUCATION -> matchEducation(c, profile, cvText);
            case PROJECT -> matchProject(c, profile);
        }).toList();
    }

    private MatchItem matchSkill(OfferCriterion c, StructuredProfile profile, String cvText) {
        String target = normalizer.normalize(c.getName());
        for (var skill : profile.skills()) {
            if (normalizer.normalize(skill.name()).equals(target)) {
                String ev = skill.evidence() == null || skill.evidence().isBlank() ? "Identifié par l'analyse" : skill.evidence();
                return MatchItem.of(c, 1, true, ev);
            }
        }
        // Filet de sécurité : recherche directe dans le texte (le LLM peut oublier une compétence)
        for (String variant : normalizer.variantsOf(target)) {
            if (TextUtils.containsWord(cvText, variant)) {
                return MatchItem.of(c, 1, true, "Mention trouvée dans le CV : « " + variant + " »");
            }
        }
        return MatchItem.of(c, 0, false, "Non identifié dans le CV");
    }

    private MatchItem matchExperience(OfferCriterion c, StructuredProfile profile) {
        double years = profile.computeYears();
        int required = c.getThreshold() != null && c.getThreshold() > 0 ? c.getThreshold() : 1;
        double ratio = Math.min(1.0, years / required);
        return MatchItem.of(c, ratio, ratio >= 1.0, "%.1f an(s) détecté(s), %d requis".formatted(years, required));
    }

    private MatchItem matchEducation(OfferCriterion c, StructuredProfile profile, String cvText) {
        String education = TextUtils.fold(profile.education().stream()
                .map(e -> TextUtils.nz(e.degree()) + " " + TextUtils.nz(e.field()) + " " + TextUtils.nz(e.institution()))
                .collect(Collectors.joining(" ")));
        for (String keyword : Arrays.stream(c.getName().split("\\|")).map(String::strip).filter(k -> !k.isEmpty()).toList()) {
            if (TextUtils.containsWord(education, keyword)) {
                return MatchItem.of(c, 1, true, "Formation correspondante : « " + keyword + " »");
            }
        }
        for (String keyword : c.getName().split("\\|")) {
            if (!keyword.isBlank() && TextUtils.containsWord(cvText, keyword.strip())) {
                return MatchItem.of(c, 1, true, "Mention trouvée dans le CV : « " + keyword.strip() + " »");
            }
        }
        return MatchItem.of(c, 0, false, "Formation non identifiée");
    }

    private MatchItem matchProject(OfferCriterion c, StructuredProfile profile) {
        int count = profile.projects().size();
        int required = c.getThreshold() != null && c.getThreshold() > 0 ? c.getThreshold() : 1;
        double ratio = Math.min(1.0, (double) count / required);
        return MatchItem.of(c, ratio, ratio >= 1.0, count + " projet(s) identifié(s), " + required + " requis");
    }
}
