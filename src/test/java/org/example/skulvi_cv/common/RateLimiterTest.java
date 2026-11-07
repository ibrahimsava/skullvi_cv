package org.example.skulvi_cv.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limiteur de débit du dépôt public de candidatures.
 *
 * <p>Il protège la ressource la plus coûteuse du service : sans lui, un script peut lancer des
 * analyses IA en boucle gratuitement et saturer la file de tous les clients.
 */
class RateLimiterTest {

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiter();
    }

    @Test
    void laisse_passer_jusqu_au_plafond() {
        for (int i = 0; i < 5; i++) {
            int appel = i + 1;
            assertThatCode(() -> limiter.check("ip-1", 5, Duration.ofMinutes(10)))
                    .as("appel %d sur 5", appel)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("l'appel au-delà du plafond répond 429")
    void refuse_au_dela_du_plafond() {
        for (int i = 0; i < 5; i++) {
            limiter.check("ip-1", 5, Duration.ofMinutes(10));
        }

        assertThatThrownBy(() -> limiter.check("ip-1", 5, Duration.ofMinutes(10)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("chaque clé a son propre compteur : un visiteur n'en bloque pas un autre")
    void les_cles_sont_independantes() {
        for (int i = 0; i < 5; i++) {
            limiter.check("ip-1", 5, Duration.ofMinutes(10));
        }

        assertThatThrownBy(() -> limiter.check("ip-1", 5, Duration.ofMinutes(10)))
                .isInstanceOf(ApiException.class);

        assertThatCode(() -> limiter.check("ip-2", 5, Duration.ofMinutes(10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la fenêtre écoulée remet le compteur à zéro")
    void la_fenetre_se_renouvelle() throws InterruptedException {
        Duration courte = Duration.ofMillis(120);

        limiter.check("ip-1", 1, courte);
        assertThatThrownBy(() -> limiter.check("ip-1", 1, courte))
                .isInstanceOf(ApiException.class);

        Thread.sleep(200);

        assertThatCode(() -> limiter.check("ip-1", 1, courte)).doesNotThrowAnyException();
    }

    /**
     * Le compteur est incrémenté de façon atomique.
     *
     * <p>Avec un « je lis puis j'écris » non atomique, des requêtes simultanées liraient la même
     * valeur et le plafond serait dépassé — exactement la situation d'une attaque, où les appels
     * arrivent tous en même temps.
     */
    @Test
    @DisplayName("le plafond tient même sous appels simultanés")
    void resiste_aux_appels_simultanes() throws InterruptedException {
        int threads = 20;
        int plafond = 5;
        AtomicInteger acceptes = new AtomicInteger();
        CountDownLatch depart = new CountDownLatch(1);
        CountDownLatch fini = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        depart.await();
                        limiter.check("rafale", plafond, Duration.ofMinutes(10));
                        acceptes.incrementAndGet();
                    } catch (ApiException refuse) {
                        // Attendu pour la majorité des appels.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        fini.countDown();
                    }
                });
            }

            depart.countDown();
            assertThat(fini.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(acceptes.get()).isEqualTo(plafond);
    }
}
