package org.example.skulvi_cv.analysis;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analysis.StructuredProfile.Education;
import org.example.skulvi_cv.analysis.StructuredProfile.Skill;
import org.example.skulvi_cv.common.TextUtils;
import org.example.skulvi_cv.matching.SkillNormalizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plan B sans IA : dictionnaire de compétences + expressions régulières. Activer avec LLM_PROVIDER=rules. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "talent.llm.provider", havingValue = "rules")
public class RuleBasedProfileExtractor implements ProfileExtractor {

    // On exige le mot « expérience » à côté du nombre d'années, pour ne pas confondre avec l'âge.
    private static final Pattern YEARS_BEFORE = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:ans|an|years|year)\\s*(?:d['’]\\s*)?(?:experience|exp\\b)");
    private static final Pattern YEARS_AFTER = Pattern.compile(
            "experience[^\\n]{0,30}?(\\d{1,2})\\s*\\+?\\s*(?:ans|an|years|year)");
    private static final Pattern DEGREE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(doctorat|phd|master|licence|bachelor|ingenieur|engineer|bts|dut)(?![\\p{L}\\p{N}])");
    private static final Pattern FIELD = Pattern.compile(
            "informatique|computer science|genie logiciel|software engineering|data science|reseaux|telecom");
    private static final Pattern PROJECT_HEADER = Pattern.compile("^\\s*(projets?|projects?|realisations?)\\b.*");
    private static final Pattern OTHER_HEADER = Pattern.compile(
            "^\\s*(experiences?|formations?|competences?|skills|education|langues?|languages?|certifications?|contact|profil)\\b.*");

    private final SkillNormalizer normalizer;

    @Override
    public StructuredProfile extract(String text) {
        String folded = TextUtils.fold(text);
        return new StructuredProfile(skills(text, folded), education(folded), List.of(), projects(text),
                List.of(), List.of(), years(folded));
    }

    private List<Skill> skills(String text, String folded) {
        List<Skill> result = new ArrayList<>();
        for (String known : SkillNormalizer.KNOWN) {
            for (String variant : normalizer.variantsOf(known)) {
                Matcher m = TextUtils.wordPattern(variant).matcher(folded);
                if (m.find()) {
                    result.add(new Skill(known, snippet(text, m.start(), m.end())));
                    break;
                }
            }
        }
        return result;
    }

    private Double years(String folded) {
        int max = 0;
        for (Pattern p : List.of(YEARS_BEFORE, YEARS_AFTER)) {
            Matcher m = p.matcher(folded);
            while (m.find()) {
                int v = Integer.parseInt(m.group(1));
                if (v <= 40) max = Math.max(max, v);
            }
        }
        return max > 0 ? (double) max : null;
    }

    private List<Education> education(String folded) {
        Matcher f = FIELD.matcher(folded);
        String field = f.find() ? f.group() : null;
        List<Education> result = new ArrayList<>();
        Matcher d = DEGREE.matcher(folded);
        while (d.find() && result.size() < 3) {
            result.add(new Education(d.group(1), field, null));
        }
        return result;
    }

    private List<String> projects(String text) {
        List<String> result = new ArrayList<>();
        boolean inSection = false;
        for (String line : text.split("\n")) {
            String f = TextUtils.fold(line);
            if (PROJECT_HEADER.matcher(f).matches()) { inSection = true; continue; }
            if (inSection) {
                if (OTHER_HEADER.matcher(f).matches()) break;
                String t = line.strip();
                if (t.length() > 3) result.add(t.length() > 100 ? t.substring(0, 100) : t);
                if (result.size() >= 6) break;
            }
        }
        return result;
    }

    private static String snippet(String text, int start, int end) {
        int s = Math.max(0, start - 40);
        int e = Math.min(text.length(), end + 40);
        return text.substring(Math.min(s, text.length()), e).replaceAll("\\s+", " ").strip();
    }
}
