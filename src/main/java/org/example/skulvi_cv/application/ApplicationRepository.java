package org.example.skulvi_cv.application;

import org.example.skulvi_cv.scoring.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByOfferIdAndCandidateId(UUID offerId, UUID candidateId);

    List<Application> findByOfferIdOrderBySubmittedAtDesc(UUID offerId);

    List<Application> findByOwnerIdOrderBySubmittedAtDesc(UUID ownerId);

    List<Application> findByOfferIdAndStatusOrderByScoreTotalDesc(UUID offerId, ApplicationStatus status);

    long countByStatus(ApplicationStatus status);

    long countByPriority(Priority priority);

    @Query("select avg(a.scoreTotal) from Application a where a.scoreTotal is not null")
    Double averageScore();
}
