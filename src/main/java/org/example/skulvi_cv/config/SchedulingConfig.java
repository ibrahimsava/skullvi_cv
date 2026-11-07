package org.example.skulvi_cv.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Tâches planifiées.
 *
 * <p>Le worker d'analyse occupe son thread pendant toute la durée d'un appel au modèle, parfois
 * plus d'une minute. Avec le thread unique par défaut, il bloquerait toutes les autres tâches
 * planifiées — dont la bascule des essais expirés. D'où une pool de deux threads.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(2).threadNamePrefix("skulvi-sched-").build();
    }
}
