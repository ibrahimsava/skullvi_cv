package org.example.skulvi_cv.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Une analyse de CV à effectuer, persistée en base.
 *
 * <p>Remplace la file en mémoire : celle-ci perdait silencieusement toutes les analyses en
 * attente à chaque redéploiement, et un candidat dont le CV n'était jamais traité n'avait aucun
 * moyen de le savoir. Ici, un redémarrage remet simplement les tâches interrompues en attente.
 *
 * <p>La priorité vient du plan de l'organisation : un client qui paie passe devant un essai
 * gratuit quand la file est chargée. C'est la seule façon de tenir une promesse de délai sans
 * multiplier les serveurs.
 */
@Entity
@Table(name = "analysis_job", indexes = {
        @Index(name = "ix_job_status_priority", columnList = "status, priority, created_at"),
        @Index(name = "ix_job_application", columnList = "application_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    /** Plus la valeur est haute, plus la tâche passe tôt. Issue de {@code Plan.queuePriority()}. */
    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private int attempts;

    /** Une relance manuelle après échec n'est pas refacturée : l'échec vient le plus souvent de nous. */
    @Column(nullable = false)
    private boolean billable = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant finishedAt;

    @Column(length = 500)
    private String lastError;

    AnalysisJob(UUID applicationId, UUID organizationId, int priority, boolean billable) {
        this.applicationId = applicationId;
        this.organizationId = organizationId;
        this.priority = priority;
        this.billable = billable;
    }
}
