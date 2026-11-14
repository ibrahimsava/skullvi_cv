package org.example.skulvi_cv.offer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Plan du site des pages qui changent : offres d'emploi et pages carrière.
 *
 * <p>Pourquoi côté serveur. Le plan du site livré avec le front
 * ({@code frontSkulvi/public/sitemap.xml}) ne peut lister que les adresses stables : il est
 * figé au moment du build. Une nouvelle offre publiée à midi n'y figurerait qu'au prochain
 * déploiement du front — autrement dit, le moteur découvrirait l'annonce des jours après les
 * candidats, ce qui est exactement l'inverse de l'intérêt d'une annonce.
 *
 * <p>Le backend, lui, connaît les offres ouvertes à l'instant où on le lui demande. Le fichier
 * est donc toujours à jour, sans republier quoi que ce soit.
 *
 * <p><b>Le domaine vient de la requête</b>, et non d'une variable de configuration. Un plan du
 * site dont les adresses pointent vers un autre domaine que celui où il est servi est rejeté
 * en entier par les moteurs — et une variable mal renseignée en production est précisément le
 * genre d'erreur qu'on ne voit pas, puisque rien ne plante. L'en-tête {@code X-Forwarded-Host}
 * est lu en premier : derrière un serveur frontal, c'est le seul qui porte le vrai domaine.
 */
@RestController
@Tag(name = "Public", description = "Offres consultables sans compte")
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class SitemapController {

    private final OfferService offers;

    @Operation(summary = "Plan du site des offres et des pages carrière",
            description = """
                    Destiné aux moteurs de recherche, déclaré dans robots.txt. Reconstruit à
                    chaque appel : une offre publiée est immédiatement visible des moteurs.""")
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(HttpServletRequest request) {
        String base = baseUrl(request);
        LocalDate aujourdhui = LocalDate.now();

        var xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        /*
         * Les pages carrière d'abord, dédoublonnées : plusieurs offres appartiennent à la même
         * entreprise, et une adresse listée deux fois fait rejeter le fichier.
         */
        Set<String> entreprises = new LinkedHashSet<>();

        for (var offre : offers.listPublic()) {
            if (offre.organizationSlug() != null && !offre.organizationSlug().isBlank()) {
                entreprises.add(offre.organizationSlug());
            }
            // L'adresse de candidature est la page réellement indexable d'une offre : c'est
            // celle qui porte le balisage JobPosting et le formulaire.
            append(xml, base + "/postuler/" + offre.id(), aujourdhui, "daily", "0.9");
        }

        for (String slug : entreprises) {
            append(xml, base + "/c/" + escape(slug), aujourdhui, "weekly", "0.7");
        }

        return xml.append("</urlset>\n").toString();
    }

    // ---------------------------------------------------------------- interne

    private static void append(StringBuilder xml, String loc, LocalDate date,
                               String frequence, String priorite) {
        xml.append("  <url>\n")
                .append("    <loc>").append(escape(loc)).append("</loc>\n")
                .append("    <lastmod>").append(date).append("</lastmod>\n")
                .append("    <changefreq>").append(frequence).append("</changefreq>\n")
                .append("    <priority>").append(priorite).append("</priority>\n")
                .append("  </url>\n");
    }

    /**
     * Reconstitue l'adresse publique du site depuis la requête.
     *
     * <p>Derrière un serveur frontal, {@code request.getServerName()} renvoie le nom interne
     * du conteneur. Les en-têtes {@code X-Forwarded-*} portent le domaine et le schéma vus par
     * le visiteur, qui sont les seuls à faire sens dans un plan du site.
     */
    static String baseUrl(HttpServletRequest request) {
        String host = premier(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }

        String scheme = premier(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null || scheme.isBlank()) {
            // Un domaine public sert forcément en HTTPS ; une adresse locale, non. Déduire du
            // nom d'hôte évite de produire « http://skulvicv.cloud », que les moteurs
            // traitent comme un domaine distinct de la version sécurisée.
            boolean local = host.startsWith("localhost") || host.startsWith("127.0.0.1");
            scheme = local ? "http" : "https";
        }

        return scheme + "://" + host;
    }

    /** Un en-tête transféré peut contenir une liste : seul le premier élément est le client. */
    private static String premier(String entete) {
        if (entete == null) return null;
        int virgule = entete.indexOf(',');
        return (virgule < 0 ? entete : entete.substring(0, virgule)).trim();
    }

    /** Échappe les caractères interdits dans une adresse XML. */
    private static String escape(String valeur) {
        return valeur.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
