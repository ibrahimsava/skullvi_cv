package org.example.skulvi_cv.org;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Génération de l'identifiant lisible d'une organisation.
 *
 * <p>Ce fragment finit dans une URL publique ({@code /c/cabinet-leon-et-fils}) partagée par le
 * client lui-même. Un accent ou une apostrophe mal traités donneraient une adresse cassée, ou
 * un lien que personne ne peut recopier.
 */
class OrganizationServiceSlugTest {

    @ParameterizedTest
    @CsvSource({
            "'Cabinet Léon & Fils', cabinet-leon-fils",
            "'Tech Recrute', tech-recrute",
            "'ACME', acme",
            "'  Espaces   multiples  ', espaces-multiples",
            "'Crèche & Cie (Paris)', creche-cie-paris",
            "'Hôtel Châteauneuf', hotel-chateauneuf",
            "'A.B.C. Conseil', a-b-c-conseil",
            "'Groupe 2000', groupe-2000",
            "'Société Générale Études', societe-generale-etudes",
    })
    void transforme_un_nom_en_identifiant_d_url(String name, String expected) {
        assertThat(OrganizationService.slugify(name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("aucun tiret ne traîne en début ou en fin")
    void pas_de_tiret_aux_extremites() {
        assertThat(OrganizationService.slugify("--- Entreprise ---")).isEqualTo("entreprise");
        assertThat(OrganizationService.slugify("!!! Alpha !!!")).isEqualTo("alpha");
    }

    @Test
    @DisplayName("un nom sans aucun caractère exploitable donne une chaîne vide, pas une erreur")
    void nom_sans_caractere_utilisable() {
        // L'appelant remplace alors par une valeur de repli ; il ne doit pas avoir d'exception.
        assertThat(OrganizationService.slugify("!!!")).isEmpty();
        assertThat(OrganizationService.slugify("   ")).isEmpty();
    }

    @Test
    @DisplayName("un nom très long est tronqué sans laisser de tiret final")
    void tronque_proprement_un_nom_long() {
        String longName = "Cabinet de recrutement specialise dans les metiers du numerique et de la data";

        String slug = OrganizationService.slugify(longName);

        assertThat(slug).hasSizeLessThanOrEqualTo(50);
        assertThat(slug).doesNotEndWith("-");
        assertThat(slug).doesNotStartWith("-");
    }

    @Test
    @DisplayName("le résultat ne contient que des caractères valables dans une URL")
    void seuls_des_caracteres_d_url() {
        String slug = OrganizationService.slugify("Ékip' & Co — Recrutement (Lyon) #1");
        assertThat(slug).matches("[a-z0-9]+(-[a-z0-9]+)*");
    }
}
