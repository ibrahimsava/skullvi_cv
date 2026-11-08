package org.example.skulvi_cv.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Retrouve l'organisation depuis l'identifiant client du prestataire de paiement. */
    Optional<Organization> findByBillingCustomerId(String billingCustomerId);

    Optional<Organization> findByBillingSubscriptionId(String billingSubscriptionId);

    long countByCreatedAtAfter(java.time.Instant since);
}
