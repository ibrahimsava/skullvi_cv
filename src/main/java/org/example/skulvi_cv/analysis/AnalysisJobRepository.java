package org.example.skulvi_cv.analysis;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {

    /**
     * Réserve la prochaine tâche à traiter.
     *
     * <p>{@code PESSIMISTIC_WRITE} avec un délai de {@code -2} se traduit par un
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} : si un jour un second conteneur backend tourne,
     * les deux workers ne prendront jamais la même tâche. Sans cela, le même CV serait analysé
     * deux fois et facturé deux fois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select j from AnalysisJob j where j.status = :status order by j.priority desc, j.createdAt asc")
    List<AnalysisJob> claimable(@Param("status") JobStatus status, Pageable pageable);

    /** Remet en attente les tâches interrompues par un arrêt du processus. */
    @Modifying
    @Query("update AnalysisJob j set j.status = org.example.skulvi_cv.analysis.JobStatus.PENDING, j.startedAt = null " +
            "where j.status = org.example.skulvi_cv.analysis.JobStatus.RUNNING")
    int requeueStale();

    long countByStatus(JobStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, JobStatus status);

    boolean existsByApplicationIdAndStatusIn(UUID applicationId, List<JobStatus> statuses);
}
