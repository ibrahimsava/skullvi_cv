package org.example.skulvi_cv.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.skulvi_cv.common.Json;
import org.example.skulvi_cv.config.TalentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Appelle un LLM local via Ollama (gratuit, aucune donnée ne quitte la machine). */
@Component
@ConditionalOnProperty(name = "talent.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaProfileExtractor implements ProfileExtractor {

    // dans la classe :
    private static final Logger log = LoggerFactory.getLogger(OllamaProfileExtractor.class);


    private static final int MAX_CHARS = 12_000;

    private static final String SYSTEM_PROMPT = """
            Tu es un extracteur d'informations pour CV. Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour, de la forme :
            {
              "skills": [{"name": "Java", "evidence": "extrait exact du CV"}],
              "education": [{"degree": "Master", "field": "Informatique", "institution": "..."}],
              "experience": [{"role": "...", "company": "...", "years": 2.0, "technologies": ["Java"]}],
              "projects": ["titre court du projet"],
              "languages": ["Français"],
              "certifications": [],
              "totalExperienceYears": 3.0
            }
            Règles :
            - N'invente rien. Si une information est absente, utilise une liste vide ou null.
            - "evidence" doit être un extrait copié mot pour mot du CV (120 caractères maximum).
            - Utilise les noms standards des technologies (PostgreSQL, Spring Boot, Docker...).
            - "totalExperienceYears" = durée cumulée d'expérience professionnelle (stages inclus). Ne la déduis jamais de l'âge.
            - Ignore le nom, l'âge, le genre, la nationalité, la photo et la situation familiale.
            """;

    private final RestClient client;
    private final String model;

    public OllamaProfileExtractor(TalentProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.llm().timeoutSeconds()));
        this.client = RestClient.builder().baseUrl(props.llm().baseUrl()).requestFactory(factory).build();
        this.model = props.llm().model();
    }

    @Override
    public StructuredProfile extract(String cvText) {
        String text = cvText.length() > MAX_CHARS ? cvText.substring(0, MAX_CHARS) : cvText;

        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "keep_alive", "30m",
                "options", Map.of("temperature", 0, "num_ctx", 8192),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "CV :\n" + text)));

        String raw;
        try {
            raw = client.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Service IA indisponible (Ollama) : " + e.getMessage(), e);
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Réponse IA vide");
        }
        log.debug("Réponse brute Ollama : {}", raw);

        OllamaResponse response = Json.read(raw, OllamaResponse.class);
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new IllegalStateException("Réponse IA vide");
        }
        return parse(response.message().content());
    }




    static StructuredProfile parse(String content) {
        String json = content.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        try {
            return Json.read(json, StructuredProfile.class);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Réponse IA invalide (JSON non conforme)", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OllamaResponse(Message message) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Message(String role, String content) {}
    }
}
