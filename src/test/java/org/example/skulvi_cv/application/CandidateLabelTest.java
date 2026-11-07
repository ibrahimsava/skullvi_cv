package org.example.skulvi_cv.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction des statuts internes en phrases montrées au candidat.
 *
 * <p>Le point important n'est pas la formulation, c'est ce qui ne doit <b>pas</b> fuiter : les
 * états techniques de l'analyse. Qu'une extraction de PDF ait échoué chez nous ne concerne pas
 * le candidat, et le lui annoncer l'inquiéterait pour un incident qu'il ne peut pas corriger.
 */
class CandidateLabelTest {

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void chaque_statut_a_une_phrase_lisible(ApplicationStatus status) {
        String label = ApplicationService.candidateLabel(status);

        assertThat(label).isNotBlank();
        // Aucun nom d'état technique ne doit apparaître tel quel.
        assertThat(label).doesNotContain("_");
        assertThat(label).isNotEqualTo(status.name());
    }

    @Test
    @DisplayName("un échec d'analyse est présenté comme un dossier en cours d'examen")
    void un_echec_technique_reste_invisible() {
        String echec = ApplicationService.candidateLabel(ApplicationStatus.ANALYSIS_FAILED);

        assertThat(echec).isEqualTo("En cours d'examen");
        assertThat(echec.toLowerCase()).doesNotContain("échec", "erreur", "analyse");
    }

    @Test
    @DisplayName("tous les états d'analyse donnent la même phrase : le candidat n'y voit rien")
    void les_etats_d_analyse_sont_regroupes() {
        String attendu = "En cours d'examen";

        assertThat(ApplicationService.candidateLabel(ApplicationStatus.ANALYZING)).isEqualTo(attendu);
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.ANALYZED)).isEqualTo(attendu);
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.ANALYSIS_FAILED)).isEqualTo(attendu);
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.SCORED)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("seules les décisions du recruteur se distinguent")
    void les_decisions_humaines_sont_annoncees() {
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.SHORTLISTED))
                .isEqualTo("Retenue pour la suite");
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.REJECTED))
                .isEqualTo("Non retenue");
    }

    @Test
    void le_depot_est_confirme() {
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.SUBMITTED))
                .isEqualTo("Candidature reçue");
        assertThat(ApplicationService.candidateLabel(ApplicationStatus.CV_UPLOADED))
                .isEqualTo("Candidature reçue");
    }
}
