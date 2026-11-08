package org.example.skulvi_cv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan} plutôt qu'une liste de classes à déclarer une par une :
 * tout enregistrement annoté {@code @ConfigurationProperties} du projet est pris en compte, et
 * on n'oublie pas d'ajouter le nouveau à la liste — oubli qui se traduit par une configuration
 * silencieusement vide.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SkulviCvApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkulviCvApplication.class, args);
    }

}
