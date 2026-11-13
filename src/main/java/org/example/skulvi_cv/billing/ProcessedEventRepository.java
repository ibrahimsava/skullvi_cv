package org.example.skulvi_cv.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Réserve un événement, ou signale qu'il a déjà été traité.
     *
     * <p>Un seul {@code INSERT ... ON CONFLICT DO NOTHING} : c'est la base qui arbitre. Deux
     * livraisons simultanées du même événement se présentent, une seule insère une ligne,
     * l'autre reçoit zéro et s'arrête. Un « je regarde puis j'insère » en deux temps laisserait
     * passer les deux.
     *
     * @return 1 si l'événement est nouveau et doit être traité, 0 s'il l'a déjà été
     */
    @Modifying
    @Query(value = """
            insert into processed_event (event_id, event_type, processed_at)
            values (:id, :type, :now)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int claim(@Param("id") String eventId,
              @Param("type") String eventType,
              @Param("now") Instant now);

    /**
     * Purge les traces anciennes.
     *
     * <p>Le prestataire n'insiste jamais plusieurs jours : garder quelques semaines suffit à
     * couvrir toutes les tentatives, et évite une table qui grossit indéfiniment.
     */
    @Modifying
    @Query("delete from ProcessedEvent e where e.processedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
