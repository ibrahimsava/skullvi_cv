package org.example.skulvi_cv.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<Membership> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
