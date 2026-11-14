package org.example.skulvi_cv.offer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconstitution du domaine public dans le plan du site.
 *
 * <p>Un plan du site dont les adresses pointent vers un autre domaine que celui où il est servi
 * est rejeté en entier par les moteurs de recherche. Et l'échec est silencieux : le fichier se
 * télécharge normalement, rien ne plante, les pages ne sont simplement jamais indexées. C'est
 * exactement le genre de panne qu'on ne découvre que des semaines plus tard, en se demandant
 * pourquoi le site ne remonte sur rien.
 */
class SitemapControllerTest {

    private static MockHttpServletRequest requete(String... entetes) {
        var request = new MockHttpServletRequest();
        // Valeur par défaut d'une requête simulée : écrasée par les en-têtes transférés quand
        // il y en a, comme derrière un serveur frontal.
        request.setServerName("localhost");
        for (int i = 0; i < entetes.length; i += 2) {
            request.addHeader(entetes[i], entetes[i + 1]);
        }
        return request;
    }

    @Test
    @DisplayName("derrière un serveur frontal, le domaine vient de X-Forwarded-Host")
    void prefere_l_entete_transfere() {
        // Le cas réel en production : nginx reçoit la requête sur le vrai domaine et la relaie
        // au conteneur, qui ne connaît que son propre nom interne.
        var request = requete(
                "X-Forwarded-Host", "skulvicv.cloud",
                "X-Forwarded-Proto", "https",
                "Host", "backend:8080");

        assertThat(SitemapController.baseUrl(request)).isEqualTo("https://skulvicv.cloud");
    }

    @Test
    @DisplayName("un en-tête transféré peut contenir une liste : seul le premier compte")
    void garde_le_premier_hote_de_la_chaine() {
        // Deux serveurs frontaux en cascade ajoutent chacun leur valeur. Le premier élément est
        // celui vu par le visiteur ; prendre la liste entière produirait une adresse invalide.
        var request = requete(
                "X-Forwarded-Host", "skulvicv.cloud, interne.local",
                "X-Forwarded-Proto", "https, http");

        assertThat(SitemapController.baseUrl(request)).isEqualTo("https://skulvicv.cloud");
    }

    @Test
    @DisplayName("sans en-tête transféré, on retombe sur Host")
    void repli_sur_l_entete_host() {
        assertThat(SitemapController.baseUrl(requete("Host", "skulvicv.cloud")))
                .isEqualTo("https://skulvicv.cloud");
    }

    @Test
    @DisplayName("un domaine public est supposé servi en HTTPS")
    void deduit_le_schema_du_nom_d_hote() {
        /*
         * « http://domaine » et « https://domaine » sont deux sites distincts pour un moteur.
         * Émettre les adresses en clair alors que le site est servi en HTTPS ferait indexer une
         * version qui redirige, et diviserait l'autorité de chaque page entre les deux.
         */
        assertThat(SitemapController.baseUrl(requete("Host", "skulvicv.cloud")))
                .startsWith("https://");

        // En local, à l'inverse : HTTPS n'y est pas servi, l'adresse serait injoignable.
        assertThat(SitemapController.baseUrl(requete("Host", "localhost:4200")))
                .isEqualTo("http://localhost:4200");
        assertThat(SitemapController.baseUrl(requete("Host", "127.0.0.1:8080")))
                .isEqualTo("http://127.0.0.1:8080");
    }

    @Test
    @DisplayName("le schéma annoncé par le serveur frontal l'emporte sur la déduction")
    void respecte_le_schema_transfere() {
        // Une préproduction servie en clair sur un vrai domaine existe : on ne doit pas lui
        // imposer HTTPS sous prétexte que son nom n'est pas « localhost ».
        var request = requete("X-Forwarded-Host", "preprod.skulvicv.cloud",
                "X-Forwarded-Proto", "http");

        assertThat(SitemapController.baseUrl(request)).isEqualTo("http://preprod.skulvicv.cloud");
    }
}
