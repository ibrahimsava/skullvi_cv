package org.example.skulvi_cv.common;

// Spring Boot 4 utilise Jackson 3. Si ton projet est sur Jackson 2, remplace l'import par :
// import com.fasterxml.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper;

public final class Json {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON invalide : " + e.getMessage(), e);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation JSON impossible : " + e.getMessage(), e);
        }
    }
}
