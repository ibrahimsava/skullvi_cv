package org.example.skulvi_cv.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.skulvi_cv.Utilisateur.AppUser;

import java.time.Instant;
import java.util.UUID;

/**
 * Rattache un utilisateur à une organisation avec un rôle.
 *
 * <p>Table séparée (et non une colonne sur {@code AppUser}) parce qu'un même compte peut à terme
 * appartenir à plusieurs organisations — un consultant qui travaille pour deux cabinets, par
 * exemple. Le jeton ne porte qu'une organisation à la fois, celle du contexte courant.
 */
@Entity
@Table(name = "membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_org_user",
                columnNames = {"organization_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.RECRUITER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
