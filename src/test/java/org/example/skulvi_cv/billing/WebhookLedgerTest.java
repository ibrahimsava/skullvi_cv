package org.example.skulvi_cv.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Idempotence des webhooks de paiement.
 *
 * <p>C'est la protection qui empêche qu'un même paiement soit appliqué deux fois. Le
 * prestataire garantit une livraison « au moins une fois » : il réessaie tant qu'il n'a pas
 * reçu un 2xx, et un délai réseau suffit à lui faire renvoyer un événement déjà honoré.
 */
@ExtendWith(MockitoExtension.class)
class WebhookLedgerTest {

    @Mock private ProcessedEventRepository processed;

    private WebhookLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new WebhookLedger(processed);
    }

    @Test
    @DisplayName("un événement nouveau est accepté")
    void accepte_un_evenement_nouveau() {
        when(processed.claim(eq("evt_1"), anyString(), any(Instant.class))).thenReturn(1);

        assertThat(ledger.claim("evt_1", "checkout.session.completed")).isTrue();
    }

    @Test
    @DisplayName("un événement déjà vu est refusé : c'est tout l'objet du registre")
    void refuse_un_doublon() {
        // La base n'a inséré aucune ligne : l'identifiant y était déjà.
        when(processed.claim(eq("evt_1"), anyString(), any(Instant.class))).thenReturn(0);

        assertThat(ledger.claim("evt_1", "checkout.session.completed")).isFalse();
    }

    @Test
    @DisplayName("c'est la base qui arbitre, pas une lecture préalable")
    void aucune_lecture_prealable() {
        when(processed.claim(anyString(), anyString(), any(Instant.class))).thenReturn(1);

        ledger.claim("evt_1", "type");

        /*
         * Point central : aucune vérification d'existence avant l'insertion. Un
         * « existsById » suivi d'un « save » laisserait une fenêtre entre les deux, et deux
         * livraisons simultanées passeraient toutes les deux. Ici la contrainte de clé
         * primaire tranche, en une seule requête.
         */
        verify(processed, never()).existsById(anyString());
        verify(processed, never()).findById(anyString());
        verify(processed).claim(eq("evt_1"), eq("type"), any(Instant.class));
    }

    @Test
    @DisplayName("un événement sans identifiant est traité : mieux vaut doubler que perdre")
    void evenement_sans_identifiant() {
        // Impossible de dédoublonner sans identifiant. Entre risquer un doublon et risquer
        // de perdre un paiement, on choisit le doublon — il est rattrapable.
        assertThat(ledger.claim(null, "type")).isTrue();
        assertThat(ledger.claim("", "type")).isTrue();
        assertThat(ledger.claim("   ", "type")).isTrue();

        verify(processed, never()).claim(anyString(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("un échec de traitement libère la trace, sinon l'événement serait perdu")
    void libere_apres_echec() {
        ledger.release("evt_1");

        /*
         * Sans ce retrait, la nouvelle tentative du prestataire serait prise pour un doublon
         * et l'événement ne serait jamais honoré : un paiement perdu en silence, ce qui est
         * pire qu'un doublon.
         */
        verify(processed).deleteById("evt_1");
    }

    @Test
    void la_liberation_ignore_un_identifiant_vide() {
        ledger.release(null);
        ledger.release("");

        verify(processed, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("la purge ne retire que les traces anciennes")
    void purge_les_traces_anciennes() {
        when(processed.deleteOlderThan(any(Instant.class))).thenReturn(3);

        ledger.purge();

        // La date limite doit être dans le passé : purger « avant maintenant » effacerait
        // la trace d'un événement traité à l'instant, et rouvrirait la porte au doublon.
        verify(processed).deleteOlderThan(org.mockito.ArgumentMatchers.argThat(
                before -> before.isBefore(Instant.now().minusSeconds(60))));
    }
}
