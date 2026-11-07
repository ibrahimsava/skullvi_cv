package org.example.skulvi_cv.application;

import org.example.skulvi_cv.scoring.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Comme pour les offres, chaque lecture est filtrée par organisation — y compris les compteurs
 * du tableau de bord, qui affichaient auparavant les chiffres de toute la plateforme.
 */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByOfferIdAndCandidateId(UUID offerId, UUID candidateId);

    Optional<Application> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Application> findByOfferIdAndOrganizationIdOrderBySubmittedAtDesc(UUID offerId, UUID organizationId);

    List<Application> findByOwnerIdOrderBySubmittedAtDesc(UUID ownerId);

    Optional<Application> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Application> findByOfferIdAndOrganizationIdAndStatusOrderByScoreTotalDesc(
            UUID offerId, UUID organizationId, ApplicationStatus status);

    long countByOrganizationId(UUID organizationId);

    long countByOfferIdAndOrganizationId(UUID offerId, UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, ApplicationStatus status);

    long countByOrganizationIdAndPriority(UUID organizationId, Priority priority);

    @Query("select avg(a.scoreTotal) from Application a " +
            "where a.organization.id = :org and a.scoreTotal is not null")
    Double averageScore(@Param("org") UUID organizationId);

    /** Le CV le plus récemment analysé d'un candidat connecté : alimente son espace personnel. */
    @Query("select a from Application a where a.owner.id = :owner and a.profileJson is not null " +
            "order by a.submittedAt desc")
    List<Application> latestAnalyzedForOwner(@Param("owner") UUID ownerId);
}
