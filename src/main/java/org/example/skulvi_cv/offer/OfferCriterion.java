package org.example.skulvi_cv.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Un critère pondéré d'une offre.
 * - SKILL      : name = compétence (ex. "Spring Boot")
 * - EXPERIENCE : threshold = nombre d'années requis
 * - EDUCATION  : name = mots-clés séparés par | (ex. "informatique|computer science")
 * - PROJECT    : threshold = nombre minimum de projets
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OfferCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    private String name;

    @Enumerated(EnumType.STRING)
    private CriterionType type;

    private boolean mandatory;
    private int weight;
    private Integer threshold;
}
