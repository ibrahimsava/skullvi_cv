package org.example.skulvi_cv.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Règles d'accès.
     *
     * <p>Le modèle a changé de nature. Avant, la règle finale était
     * {@code .anyRequest().hasRole("ADMIN")} : il n'existait qu'un seul administrateur sur toute
     * la plateforme, et sa présence suffisait à tout autoriser. Maintenant, l'autorisation se
     * joue à deux niveaux :
     *
     * <ol>
     *   <li><b>ici</b> : la requête est-elle authentifiée, et par le bon type de compte ?
     *   <li><b>dans les services</b> : l'organisation portée par le jeton filtre chaque requête
     *       SQL, et le rôle du membre autorise ou non l'écriture.
     * </ol>
     *
     * <p>Le second niveau est celui qui compte vraiment. Même si une route était ouverte trop
     * largement ici, un recruteur ne verrait jamais que les données de sa propre organisation,
     * parce qu'aucun service n'accepte d'identifiant d'organisation venant du client.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerEnabled) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/error").permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // La documentation n'est ouverte que si elle est activée (désactivée en production).
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }

                    auth
                            // Inscription et connexion
                            .requestMatchers("/api/v1/auth/**").permitAll()

                            // Visiteurs : offres, pages carrière, tarifs
                            .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()

                            // Postuler sans compte reste possible (limité en débit par adresse IP)
                            .requestMatchers(HttpMethod.POST, "/api/v1/offers/*/applications").permitAll()

                            // Espace personnel : tout compte connecté, filtré sur le sujet du jeton
                            .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()

                            // Administration de la plateforme : le compte Skulvi, pas un client
                            .requestMatchers("/api/v1/platform/**").hasRole("ADMIN")

                            /*
                             * Espace recruteur. Authentifié suffit : c'est la présence du
                             * rattachement à une organisation dans le jeton qui fait l'accès, et
                             * CurrentUser.orgId() répond 403 à un candidat qui s'égare ici.
                             * Lister les rôles un par un donnerait l'illusion d'une sécurité
                             * qui, en réalité, se joue au niveau de la requête SQL.
                             */
                            .requestMatchers("/api/v1/**").authenticated()

                            .anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(secret)));
    }

    /**
     * Origines autorisées.
     *
     * <p>La valeur est découpée sur les virgules : {@code List.of(origin)} n'en acceptait qu'une
     * seule, ce qui rendait impossible de servir à la fois le domaine de production et un
     * environnement de test — ou, demain, un sous-domaine par client.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.security.cors-origin}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));   // pour nommer le CV téléchargé
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 caractères");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
