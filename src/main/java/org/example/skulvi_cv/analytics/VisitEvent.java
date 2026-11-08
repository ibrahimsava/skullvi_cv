package org.example.skulvi_cv.analytics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une visite de page, enregistrée sans cookie ni identifiant publicitaire.
 *
 * <p><b>Ce qui n'est pas stocké, et pourquoi.</b> Pas d'adresse IP en clair, pas de cookie
 * de suivi, pas d'identifiant qui survit à la journée. Le visiteur est réduit à une empreinte
 * {@code visitorHash} calculée depuis son IP, son navigateur et un sel qui change chaque jour :
 * on peut donc compter les visiteurs distincts d'une journée, sans pouvoir suivre quelqu'un
 * d'un jour sur l'autre ni remonter à une personne. C'est ce qui permet de se passer de
 * bandeau de consentement — une mesure d'audience strictement anonyme.
 *
 * <p>Conséquence assumée : un même visiteur revenant demain est compté comme nouveau. On
 * échange un peu de précision contre l'absence de suivi individuel.
 */
@Entity
@Table(name = "visit_event", indexes = {
        @Index(name = "ix_visit_day", columnList = "day"),
        @Index(name = "ix_visit_org_day", columnList = "organization_id, day"),
        @Index(name = "ix_visit_visitor_day", columnList = "visitor_hash, day")
})
@Getter
@Setter
@NoArgsConstructor
public class VisitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Organisation concernée, quand la page en vise une : page carrière, détail d'offre,
     * formulaire de candidature. Null pour les pages générales du site.
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VisitKind kind = VisitKind.PAGE;

    /** Chemin visité, tronqué et nettoyé de ses paramètres. */
    @Column(nullable = false, length = 160)
    private String path;

    /** Empreinte quotidienne du visiteur. Ne permet pas de l'identifier. */
    @Column(name = "visitor_hash", nullable = false, length = 44)
    private String visitorHash;

    /** Domaine de provenance uniquement, jamais l'URL complète. */
    @Column(length = 120)
    private String referrerHost;

    /** Famille d'appareil, pour distinguer mobile et bureau. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceKind device = DeviceKind.UNKNOWN;

    /** Jour de la visite, dénormalisé : tous les regroupements se font par journée. */
    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
