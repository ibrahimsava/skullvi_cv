package org.example.skulvi_cv.matching;

import org.example.skulvi_cv.common.TextUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ramène les variantes d'écriture à un nom canonique (Postgres → postgresql). */
@Component
public class SkillNormalizer {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("postgres", "postgresql"),
            Map.entry("postgre sql", "postgresql"),
            Map.entry("psql", "postgresql"),
            Map.entry("springboot", "spring boot"),
            Map.entry("spring-boot", "spring boot"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("node", "node.js"),
            Map.entry("nodejs", "node.js"),
            Map.entry("node js", "node.js"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("mongo", "mongodb"),
            Map.entry("reactjs", "react"),
            Map.entry("react.js", "react"),
            Map.entry("angularjs", "angular"),
            Map.entry("vuejs", "vue"),
            Map.entry("vue.js", "vue"),
            Map.entry("rabbit mq", "rabbitmq"),
            Map.entry("c sharp", "c#"),
            Map.entry("dotnet", ".net"),
            Map.entry("rest", "rest api"),
            Map.entry("restful", "rest api"),
            Map.entry("api rest", "rest api"),
            Map.entry("ci cd", "ci/cd"),
            Map.entry("cicd", "ci/cd"),
            Map.entry("microservice", "microservices"),
            Map.entry("micro-services", "microservices"));

    /** Compétences recherchées par le mode « règles ». */
    public static final List<String> KNOWN = List.of(
            "java", "spring boot", "hibernate", "jpa", "maven", "gradle", "junit", "kotlin",
            "sql", "postgresql", "mysql", "mongodb", "redis", "rabbitmq", "kafka",
            "docker", "kubernetes", "git", "github", "gitlab", "ci/cd", "jenkins",
            "aws", "azure", "linux", "rest api", "microservices",
            "python", "javascript", "typescript", "node.js", "angular", "react", "vue",
            "html", "css", "c#", ".net", "php", "laravel", "flutter", "c++");

    public String normalize(String raw) {
        String s = TextUtils.fold(raw).replaceAll("\\s+", " ").strip();
        return ALIASES.getOrDefault(s, s);
    }

    /** Le nom canonique + toutes ses variantes connues. */
    public List<String> variantsOf(String canonical) {
        String c = normalize(canonical);
        List<String> variants = new ArrayList<>(List.of(c));
        ALIASES.forEach((alias, target) -> {
            if (target.equals(c)) variants.add(alias);
        });
        return variants;
    }
}
