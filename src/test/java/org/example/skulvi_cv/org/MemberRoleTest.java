package org.example.skulvi_cv.org;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Droits attachés à chaque rôle dans une organisation. */
class MemberRoleTest {

    @Test
    @DisplayName("seul le propriétaire touche à l'abonnement et à l'équipe")
    void seul_le_proprietaire_administre() {
        assertThat(MemberRole.OWNER.canManageBilling()).isTrue();
        assertThat(MemberRole.OWNER.canManageMembers()).isTrue();

        assertThat(MemberRole.RECRUITER.canManageBilling()).isFalse();
        assertThat(MemberRole.RECRUITER.canManageMembers()).isFalse();
        assertThat(MemberRole.VIEWER.canManageBilling()).isFalse();
        assertThat(MemberRole.VIEWER.canManageMembers()).isFalse();
    }

    @Test
    @DisplayName("le lecteur ne peut rien modifier : c'est tout l'intérêt du rôle")
    void le_lecteur_est_en_consultation_seule() {
        assertThat(MemberRole.VIEWER.canWrite()).isFalse();
        assertThat(MemberRole.OWNER.canWrite()).isTrue();
        assertThat(MemberRole.RECRUITER.canWrite()).isTrue();
    }

    @Test
    @DisplayName("un rôle qui administre peut forcément écrire")
    void administrer_implique_ecrire() {
        for (MemberRole role : MemberRole.values()) {
            if (role.canManageBilling() || role.canManageMembers()) {
                assertThat(role.canWrite())
                        .as("%s administre, il doit pouvoir écrire", role)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("aucun statut ne bloque l'écriture : c'est le quota du plan qui limite")
    void le_statut_ne_bloque_jamais_l_ecriture() {
        // Un client dont la carte a expiré redescend sur les limites du gratuit, il ne perd
        // pas l'accès. Couper entièrement le service fait perdre le client, pas gagner le
        // paiement.
        for (OrgStatus status : OrgStatus.values()) {
            assertThat(status.allowsWrite())
                    .as("le statut %s doit laisser écrire", status)
                    .isTrue();
        }
    }
}
