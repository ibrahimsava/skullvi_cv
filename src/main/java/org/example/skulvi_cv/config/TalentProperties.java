package org.example.skulvi_cv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Enregistré par le {@code @ConfigurationPropertiesScan} de la classe de démarrage. */
@ConfigurationProperties(prefix = "talent")
public record TalentProperties(Llm llm, Scoring scoring, Storage storage) {

    public record Llm(String provider,
                      String baseUrl,
                      String model,
                      int timeoutSeconds) {}

    public record Scoring(int highThreshold,
                          int reviewThreshold,
                          int secondaryThreshold,
                          int mandatoryMissingCap) {}

    public record Storage(String dir) {}
}
