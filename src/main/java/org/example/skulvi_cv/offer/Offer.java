package org.example.skulvi_cv.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.skulvi_cv.org.Organization;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "ix_offer_org_status", columnList = "organization_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Organisation propriétaire. Toute lecture d'offre est filtrée par cette colonne. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String domain;
    private String level;
    private Integer minExperienceYears;
    private LocalDate startDate;
    private LocalDate closingDate;

    @Enumerated(EnumType.STRING)
    private OfferStatus status = OfferStatus.OPEN;

    @OneToMany(mappedBy = "offer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OfferCriterion> criteria = new ArrayList<>();
}
