package org.example.skulvi_cv.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nettoyage des données de mesure avant enregistrement.
 *
 * <p>Deux enjeux, et le second est le plus important : garder une liste de pages lisible, et
 * ne jamais écrire en base une donnée qui permettrait d'identifier quelqu'un.
 */
class TrackingServiceTest {

    @ParameterizedTest
    @CsvSource({
            "'/offres', '/offres'",
            "'/offres?q=java', '/offres'",
            "'/offres#resultats', '/offres'",
            "'offres', '/offres'",
            "'', '/'",
    })
    void normalise_les_chemins_simples(String raw, String expected) {
        assertThat(TrackingService.normalizePath(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("les identifiants sont remplacés : sinon chaque offre crée sa propre ligne")
    void masque_les_identifiants() {
        assertThat(TrackingService.normalizePath("/postuler/cfe37afe-0c66-4de7-b34c-f33993f14484"))
                .isEqualTo("/postuler/:id");
        assertThat(TrackingService.normalizePath("/offres/123456"))
                .isEqualTo("/offres/:id");
    }

    @Test
    @DisplayName("un jeton glissé dans l'URL ne survit pas au nettoyage")
    void retire_la_chaine_de_requete() {
        // Une URL peut contenir n'importe quoi, y compris ce qui n'a rien à faire en base.
        assertThat(TrackingService.normalizePath("/admin?token=secret123&next=/x"))
                .isEqualTo("/admin");
    }

    @Test
    void tronque_un_chemin_demesure() {
        String tres_long = "/a".repeat(200);
        assertThat(TrackingService.normalizePath(tres_long)).hasSizeLessThanOrEqualTo(160);
    }

    @Test
    void normalise_un_chemin_nul() {
        assertThat(TrackingService.normalizePath(null)).isEqualTo("/");
    }

    // ---------------------------------------------------------------- provenance

    @ParameterizedTest
    @CsvSource({
            "'https://www.google.com/search?q=recrutement', 'google.com'",
            "'https://linkedin.com/feed', 'linkedin.com'",
            "'http://WWW.Example.FR/page', 'example.fr'",
    })
    @DisplayName("seul le domaine de provenance est gardé, jamais l'URL complète")
    void extrait_le_domaine(String referrer, String expected) {
        // L'URL complète d'un référent peut contenir une requête de recherche, donc une
        // information personnelle. Le domaine suffit à savoir d'où viennent les visiteurs.
        assertThat(TrackingService.hostOf(referrer)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "pas une url", "javascript:alert(1)" })
    void provenance_illisible_ou_absente_vaut_null(String referrer) {
        assertThat(TrackingService.hostOf(referrer)).isNull();
    }

    @Test
    void provenance_nulle_vaut_null() {
        assertThat(TrackingService.hostOf(null)).isNull();
    }

    // ---------------------------------------------------------------- appareil

    @Test
    void reconnait_les_familles_d_appareil() {
        assertThat(DeviceKind.fromUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"))
                .isEqualTo(DeviceKind.MOBILE);
        assertThat(DeviceKind.fromUserAgent("Mozilla/5.0 (Linux; Android 14) Mobile"))
                .isEqualTo(DeviceKind.MOBILE);
        assertThat(DeviceKind.fromUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
                .isEqualTo(DeviceKind.DESKTOP);
        assertThat(DeviceKind.fromUserAgent(null)).isEqualTo(DeviceKind.UNKNOWN);
        assertThat(DeviceKind.fromUserAgent("")).isEqualTo(DeviceKind.UNKNOWN);
    }

    // ---------------------------------------------------------------- empreinte

    @Test
    @DisplayName("l'empreinte ne contient aucune trace de l'adresse IP")
    void l_empreinte_ne_fuit_pas_l_adresse() {
        var fingerprint = new VisitorFingerprint();
        String hash = fingerprint.of("203.0.113.42", "Mozilla/5.0", LocalDate.of(2026, 11, 8));

        assertThat(hash).doesNotContain("203", "113", "42");
        assertThat(hash).hasSizeLessThanOrEqualTo(44);
    }

    @Test
    @DisplayName("même visiteur, même jour : même empreinte — c'est ce qui permet de dédoublonner")
    void stable_dans_la_journee() {
        var fingerprint = new VisitorFingerprint();
        LocalDate day = LocalDate.of(2026, 11, 8);

        assertThat(fingerprint.of("1.2.3.4", "UA", day))
                .isEqualTo(fingerprint.of("1.2.3.4", "UA", day));
    }

    @Test
    @DisplayName("jour suivant : empreinte différente — aucun suivi d'un jour sur l'autre")
    void change_chaque_jour() {
        var fingerprint = new VisitorFingerprint();

        assertThat(fingerprint.of("1.2.3.4", "UA", LocalDate.of(2026, 11, 8)))
                .isNotEqualTo(fingerprint.of("1.2.3.4", "UA", LocalDate.of(2026, 11, 9)));
    }

    @Test
    void deux_visiteurs_donnent_deux_empreintes() {
        var fingerprint = new VisitorFingerprint();
        LocalDate day = LocalDate.of(2026, 11, 8);

        assertThat(fingerprint.of("1.2.3.4", "UA", day))
                .isNotEqualTo(fingerprint.of("5.6.7.8", "UA", day));
    }

    @Test
    @DisplayName("deux instances ont des sels différents : le secret n'est pas devinable")
    void le_sel_est_propre_a_l_instance() {
        LocalDate day = LocalDate.of(2026, 11, 8);

        assertThat(new VisitorFingerprint().of("1.2.3.4", "UA", day))
                .isNotEqualTo(new VisitorFingerprint().of("1.2.3.4", "UA", day));
    }
}
