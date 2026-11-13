package org.example.skulvi_cv.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Trace des événements de paiement déjà traités.
 *
 * <p><b>Pourquoi c'est indispensable.</b> Le prestataire garantit une livraison « au moins une
 * fois », pas « exactement une fois » : il réessaie tant qu'il n'a pas reçu un 2xx, et un
 * simple délai réseau lui fait renvoyer un événement déjà traité. Sans ce garde-fou, le même
 * paiement serait appliqué deux fois.
 *
 * <p><b>L'identifiant de l'événement est la clé primaire.</b> C'est ce qui rend la protection
 * fiable : la base refuse l'insertion d'un doublon, et la contrainte joue même si deux
 * livraisons arrivent exactement en même temps sur deux threads. Un test « ai-je déjà vu cet
 * identifiant ? » suivi d'une insertion laisserait au contraire une fenêtre entre les deux.
 */
@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    /** Identifiant fourni par le prestataire, par exemple {@code evt_1A2b3C}. */
    @Id
    @Column(name = "event_id", length = 120, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
