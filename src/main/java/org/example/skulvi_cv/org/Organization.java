package org.example.skulvi_cv.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Le client : une entreprise ou un cabinet de recrutement.
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
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgStatus status = OrgStatus.ACTIVE;

    /** Identifiant client chez le prestataire de paiement. Créé au premier passage en caisse. */
    @Column(length = 80)
    private String billingCustomerId;

    /** Identifiant de l'abonnement en cours. Null sur le plan gratuit. */
    @Column(length = 80)
    private String billingSubscriptionId;

    /**
     * Fin de la période déjà payée.
     *
     * <p>Sert à annoncer « votre abonnement reste actif jusqu'au … » après une résiliation :
     * le client a payé le mois, il le garde.
     */
    private Instant currentPeriodEnd;

    /** Résiliation demandée : l'abonnement s'arrête à la fin de la période en cours. */
    @Column(nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * L'organisation peut-elle écrire ?
     *
     * <p>Toujours oui : le plan gratuit reste accessible en toutes circonstances. Ce sont les
     * quotas qui limitent, jamais le statut.
     */
    public boolean writable() {
        return status.allowsWrite();
    }

    public boolean hasPaidPlan() {
        return plan.isPaid();
    }
}
