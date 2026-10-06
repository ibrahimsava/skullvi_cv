package org.example.skulvi_cv.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI skulviOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Skulvi CV API")
                .version("v1")
                .description("API de gestion des offres, candidatures, analyse de CV, scoring et classement des candidats."));
    }
}