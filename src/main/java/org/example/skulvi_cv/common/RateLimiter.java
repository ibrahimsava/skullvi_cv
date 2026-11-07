package org.example.skulvi_cv.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limiteur de débit par clé, en fenêtre glissante grossière.
 *
 * <p>Il existe pour une raison concrète : le dépôt de candidature est une route publique qui
 * déclenche une analyse IA. Sans limite, un script qui envoie des PDF en boucle consomme tout le
 * temps CPU du serveur et bloque les analyses des clients qui paient. Le quota par organisation
 * ne suffit pas, puisqu'il faudrait déjà avoir accepté le fichier pour le décompter.
 *
 * <p><b>Limite connue</b> : le compteur est en mémoire. Avec plusieurs conteneurs backend, chacun
 * applique la limite de son côté, et le plafond réel est multiplié par leur nombre. Tant qu'un
 * seul conteneur tourne, c'est exact ; au-delà, il faudra déplacer les compteurs dans Redis.
 */
@Slf4j
@Component
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Consomme un jeton, ou refuse la requête.
     *
     * @param key      ce qu'on limite : une adresse IP, un email, un identifiant d'offre
     * @param maxCalls nombre d'appels autorisés dans la fenêtre
     * @param window   durée de la fenêtre
     * @throws ApiException 429 si le plafond est atteint
     */
    public void check(String key, int maxCalls, Duration window) {
        Instant now = Instant.now();

        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.startedAt().plus(window).isBefore(now)) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });

        if (current.count().incrementAndGet() > maxCalls) {
            log.warn("Débit dépassé pour « {} » : plus de {} appels en {}", key, maxCalls, window);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Patientez quelques minutes avant de réessayer.");
        }

        // Purge opportuniste : évite que la map grossisse indéfiniment sur un service exposé.
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().startedAt().plus(window).isBefore(now));
        }
    }
}
