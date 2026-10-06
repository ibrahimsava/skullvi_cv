package org.example.skulvi_cv;

import org.example.skulvi_cv.config.TalentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TalentProperties.class)
public class SkulviCvApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkulviCvApplication.class, args);
    }

}
