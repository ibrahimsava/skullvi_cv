package org.example.skulvi_cv.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distinction entre une instance locale et une instance publique.
 *
 * <p>C'est ce test qui décide si les alertes de configuration se déclenchent. Se tromper dans
 * un sens noie le journal de développement d'avertissements inutiles — qu'on apprend alors à
 * ignorer, ce qui annule l'intérêt du dispositif. Se tromper dans l'autre rend muette la seule
 * alerte qui compte vraiment : un paiement actif avec une adresse de retour locale.
 */
class ConfigurationAuditTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:4200",
            "http://localhost",
            "https://LOCALHOST:8080",
            "http://127.0.0.1:4200",
            "http://0.0.0.0:8080",
    })
    void reconnait_une_adresse_de_developpement(String url) {
        assertThat(ConfigurationAudit.estLocal(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://skulvicv.cloud",
            "https://www.skulvicv.cloud",
            "https://preprod.skulvicv.cloud",
    })
    void reconnait_une_adresse_publique(String url) {
        assertThat(ConfigurationAudit.estLocal(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "skulvi",        // le nom du produit : essayé avant le dictionnaire
            "Skulvi2024",    // le même avec du bruit autour
            "SKULVI@",
            "admin",
            "motdepasse",
            "postgres",
    })
    void repere_un_mot_de_passe_devinable(String motDePasse) {
        assertThat(ConfigurationAudit.devinable(motDePasse)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7Kq$mZr2vLpX",
            "chaise-tonnerre-45-brique",
    })
    void laisse_passer_un_mot_de_passe_correct(String motDePasse) {
        assertThat(ConfigurationAudit.devinable(motDePasse)).isFalse();
    }

    @Test
    @DisplayName("un mot de passe absent n'est pas « devinable » : le compte n'existe pas")
    void mot_de_passe_absent() {
        // ADMIN_PASSWORD vide est une configuration valide — elle signifie simplement qu'aucun
        // compte d'administration n'est créé. La signaler produirait une alerte permanente
        // sur les instances qui n'en veulent pas.
        assertThat(ConfigurationAudit.devinable("")).isFalse();
        assertThat(ConfigurationAudit.devinable(null)).isFalse();
    }

    @Test
    @DisplayName("une adresse absente compte comme locale")
    void adresse_absente() {
        /*
         * Une instance lancée sans aucune configuration est forcément en développement.
         * Traiter l'inconnu comme de la production ferait apparaître la liste complète des
         * alertes à chaque démarrage en local, et plus personne ne la lirait.
         */
        assertThat(ConfigurationAudit.estLocal(null)).isTrue();
        assertThat(ConfigurationAudit.estLocal("")).isTrue();
        assertThat(ConfigurationAudit.estLocal("   ")).isTrue();
    }
}
