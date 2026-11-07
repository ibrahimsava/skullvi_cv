package org.example.skulvi_cv.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.billing.QuotaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accès transactionnel à la file d'analyses.
 *
 * <p>Composant distinct de {@link AnalysisQueue} pour une raison technique précise : une méthode
 * {@code @Transactional} appelée depuis la même classe ne passe pas par le proxy Spring, et son
 * annotation est donc purement décorative. En sortant ces méthodes dans un bean séparé, chaque
 * appel traverse bien le proxy et ouvre la transaction courte attendue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisJobStore {

    /** Nombre de tentatives avant abandon définitif. */
    static final int MAX_ATTEMPTS = 3;

    private final AnalysisJobRepository jobs;
    private final QuotaService quotas;

    /**
     * Place une candidature dans la file et décompte le quota.
     *
     * @param billable {@code false} pour une relance manuelle après échec : on ne facture pas
     *                 deux fois un CV parce que notre extraction a échoué la première fois
     */
    @Transactional
    public void enqueue(UUID applicationId, UUID organizationId, int priority, boolean billable) {
        if (jobs.existsByApplicationIdAndStatusIn(applicationId, List.of(JobStatus.PENDING, JobStatus.RUNNING))) {
            log.debug("Candidature {} déjà dans la file", applicationId);
            return;
        }
        jobs.save(new AnalysisJob(applicationId, organizationId, priority, billable));
        if (billable) {
            quotas.recordAnalysis(organizationId);
        }
        log.info("Candidature {} en file (priorité {}, facturée : {})", applicationId, priority, billable);
    }

    /**
     * Réserve la prochaine tâche dans une transaction courte.
     *
     * <p>Le verrou de ligne est relâché dès le retour : le garder ouvert pendant les 90 secondes
     * d'un appel au modèle immobiliserait une connexion de la pool pour rien.
     *
     * @return la tâche réservée, ou {@code null} si la file est vide
     */
    @Transactional
    public AnalysisJob claimNext() {
        List<AnalysisJob> next = jobs.claimable(JobStatus.PENDING, PageRequest.of(0, 1));
        if (next.isEmpty()) {
            return null;
        }
        AnalysisJob job = next.get(0);
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setAttempts(job.getAttempts() + 1);
        return jobs.save(job);
    }

    /** Enregistre le verdict d'une tâche : terminée, à retenter, ou en échec définitif. */
    @Transactional
    public void finish(UUID jobId, boolean ok, String error) {
        AnalysisJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        if (ok) {
            job.setStatus(JobStatus.DONE);
        } else if (job.getAttempts() < MAX_ATTEMPTS) {
            // Nouvelle tentative : le modèle peut avoir simplement mis trop de temps à charger.
            job.setStatus(JobStatus.PENDING);
            job.setStartedAt(null);
        } else {
            job.setStatus(JobStatus.FAILED);
        }
        job.setLastError(error == null ? null : error.substring(0, Math.min(480, error.length())));
        job.setFinishedAt(job.getStatus() == JobStatus.PENDING ? null : Instant.now());
        jobs.save(job);
    }

    /** Remet en attente ce qui était en cours quand le processus s'est arrêté. */
    @Transactional
    public int requeueInterrupted() {
        return jobs.requeueStale();
    }

    /** Nombre d'analyses en attente ou en cours pour une organisation : affiché dans l'interface. */
    @Transactional(readOnly = true)
    public long pendingFor(UUID organizationId) {
        return jobs.countByOrganizationIdAndStatus(organizationId, JobStatus.PENDING)
                + jobs.countByOrganizationIdAndStatus(organizationId, JobStatus.RUNNING);
    }
}
