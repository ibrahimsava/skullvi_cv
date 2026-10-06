package org.example.skulvi_cv.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Profil candidat structuré, produit par l'IA (ou par les règles). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StructuredProfile(
        List<Skill> skills,
        List<Education> education,
        List<Experience> experience,
        List<String> projects,
        List<String> languages,
        List<String> certifications,
        Double totalExperienceYears) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skill(String name, String evidence) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(String degree, String field, String institution) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(String role, String company, Double years, List<String> technologies) {}

    public StructuredProfile {
        skills = skills == null ? List.of() : skills;
        education = education == null ? List.of() : education;
        experience = experience == null ? List.of() : experience;
        projects = projects == null ? List.of() : projects;
        languages = languages == null ? List.of() : languages;
        certifications = certifications == null ? List.of() : certifications;
    }

    /** Années d'expérience : valeur globale si fournie, sinon somme des expériences. */
    public double computeYears() {
        if (totalExperienceYears != null && totalExperienceYears >= 0) return totalExperienceYears;
        return experience.stream().mapToDouble(e -> e.years() == null ? 0 : e.years()).sum();
    }
}
