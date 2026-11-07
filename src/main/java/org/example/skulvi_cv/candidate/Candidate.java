package org.example.skulvi_cv.candidate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.skulvi_cv.org.Organization;

import java.util.UUID;

/**
 * Un candidat, tel que connu par <b>une</b> organisation.
 *
 * <p>La même personne qui postule chez deux clients différents donne deux lignes distinctes.
 * C'est volontaire : chaque organisation est responsable du traitement des données qu'elle
 * collecte, et une suppression demandée à un client ne doit pas effacer la candidature déposée
 * chez un autre. D'où l'unicité sur le couple (organisation, email) et non sur l'email seul.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_candidate_org_email",
        columnNames = {"organization_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String firstName;
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
}
