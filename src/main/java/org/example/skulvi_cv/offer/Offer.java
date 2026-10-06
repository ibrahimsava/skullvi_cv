package org.example.skulvi_cv.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
