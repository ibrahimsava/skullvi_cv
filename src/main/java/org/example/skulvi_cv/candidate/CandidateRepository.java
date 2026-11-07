package org.example.skulvi_cv.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    /** Un candidat est identifié par son email <b>au sein d'une organisation</b>. */
    Optional<Candidate> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);
}
