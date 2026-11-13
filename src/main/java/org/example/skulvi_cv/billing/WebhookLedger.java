package org.example.skulvi_cv.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Registre des événements de paiement déjà honorés.
 *
 * <p>Composant séparé du contrôleur pour une raison précise : la réservation doit être
 * validée en base <b>immédiatement</b>, dans sa propre transaction, avant que le traitement
 * ne commence. Si elle partageait la transaction du traitement, deux livraisons simultanées
 * ne se verraient pas l'une l'autre — chacune lirait une base où l'événement n'est pas encore
 * enregistré, et les deux passeraient.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookLedger {

    /** Durée de conservation des traces. Le prestataire n'insiste jamais aussi longtemps. */
    private static final int RETENTION_DAYS = 30;

    private final ProcessedEventRepository processed;

    /**
     * Réserve un événement.
     *
     * <p>{@code REQUIRES_NEW} : la réservation est validée tout de suite, indépendamment de
     * ce qui suit. C'est cette validation immédiate qui rend la protection efficace face à
     * deux livraisons concurrentes.
     *
     * @return {@code true} si l'événement est nouveau et doit être traité
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            // Événement sans identifiant : on le traite, faute de pouvoir le dédoublonner.
            log.warn("Événement de paiement sans identifiant : dédoublonnage impossible");
            return true;
        }
        return processed.claim(eventId, eventType, Instant.now()) > 0;
    }

    /**
     * Retire la trace d'un événement dont le traitement a échoué.
     *
     * <p>Sans ce retrait, la nouvelle tentative du prestataire serait prise pour un doublon
     * et l'événement ne serait jamais honoré — un paiement perdu en silence, ce qui est le
     * pire des deux maux.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        processed.deleteById(eventId);
    }

    /** Purge les traces anciennes, chaque nuit. */
    @Scheduled(cron = "${stripe.ledger-purge-cron:0 15 4 * * *}")
    @Transactional
    public void purge() {
        int removed = processed.deleteOlderThan(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("{} trace(s) de webhook purgée(s)", removed);
        }
    }
}
