package org.example.skulvi_cv.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Compteur de consommation, un enregistrement par organisation, mois et nature.
 *
 * <p>Agrégé plutôt que ligne par ligne : on ne garde pas un événement par analyse, seulement le
 * total du mois. C'est suffisant pour facturer, et ça évite une table qui grossit sans fin. La
 * trace détaillée d'une analyse existe déjà ailleurs, dans {@code analysis_job}.
 *
 * <p>La référence à l'organisation est un simple UUID et non une relation JPA : ce compteur est
 * incrémenté par une requête atomique, il n'a jamais besoin de charger l'entité.
 */
@Entity
@Table(name = "usage_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_usage_org_period_kind",
                columnNames = {"organization_id", "period", "kind"}))
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Mois de facturation au format {@code AAAA-MM}. */
    @Column(nullable = false, length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UsageKind kind;

    @Column(name = "used", nullable = false)
    private int used;
}
