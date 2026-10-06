package org.example.skulvi_cv.application;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.skulvi_cv.candidate.Candidate;
import org.example.skulvi_cv.offer.Offer;
import org.example.skulvi_cv.scoring.Priority;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"offer_id", "candidate_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    private Instant submittedAt = Instant.now();

    private String cvPath;
    private String cvHash;

    @Column(columnDefinition = "text")
    private String extractedText;

    /** Profil structuré extrait du CV (JSON). */
    @Column(columnDefinition = "text")
    private String profileJson;

    private Integer scoreTotal;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    /** Détail du score, critère par critère (JSON) : sert à l'explicabilité. */
    @Column(columnDefinition = "text")
    private String breakdownJson;

    @Column(length = 500)
    private String failureReason;
}
