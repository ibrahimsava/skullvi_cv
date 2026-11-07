package org.example.skulvi_cv.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Le client qui paie : une entreprise ou un cabinet de recrutement.
 *
 * <p>C'est la racine de cloisonnement de toutes les données métier. Offres, candidatures et
 * candidats portent un {@code organization_id} : deux clients ne voient jamais les données
 * l'un de l'autre.
 */
@Entity
@Table(name = "organization")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Identifiant lisible, utilisé dans les URL publiques (page carrière). */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgStatus status = OrgStatus.TRIAL;

    /** Fin de la période d'essai. Null dès que l'organisation a un abonnement payant. */
    private Instant trialEndsAt;

    /**
     * Identifiant client chez le prestataire de paiement.
     * Rempli au premier paiement ; laissé libre ici pour ne pas coupler le modèle à Stripe.
     */
    @Column(length = 80)
    private String billingCustomerId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** L'essai est-il arrivé à échéance ? */
    public boolean trialExpired() {
        return status == OrgStatus.TRIAL && trialEndsAt != null && trialEndsAt.isBefore(Instant.now());
    }

    /** L'organisation peut-elle encore écrire (créer une offre, consommer une analyse) ? */
    public boolean writable() {
        return status.allowsWrite() && !trialExpired();
    }
}
