package org.example.skulvi_cv.config;

import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.billing.StripeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrôle de cohérence de la configuration au démarrage.
 *
 * <p>Toutes les variables d'environnement ont une valeur par défaut, ce qui permet à
 * l'application de démarrer sans fichier de configuration. C'est pratique en développement, et
 * c'est un piège en production : une variable oubliée ne provoque aucune erreur, l'application
 * démarre normalement, et le défaut de développement s'applique en silence.
 *
 * <p>Le cas qui coûte le plus cher : {@code PUBLIC_URL} absent. Le défaut vaut
 * {@code http://localhost:4200}, donc l'adresse de retour envoyée au prestataire de paiement
 * renvoie le client <b>sur sa propre machine</b> après qu'il a payé. Rien ne plante, le
 * prélèvement a lieu, et le client voit une page d'erreur. On ne découvre le problème qu'au
 * premier encaissement réel, par une réclamation.
 *
 * <p>Ce composant ne corrige rien et n'empêche pas le démarrage — bloquer une mise en
 * production sur une alerte de confort serait pire. Il rend simplement visible, dans les
 * premières lignes du journal, ce qui serait resté muet.
 */
@Slf4j
@Component
public class ConfigurationAudit {

    /** En dessous, un mot de passe se casse hors ligne en quelques heures. */
    private static final int LONGUEUR_MOT_DE_PASSE_MINIMALE = 12;

    /** 32 octets, soit la taille d'une clé HMAC-SHA256 digne de ce nom. */
    private static final int LONGUEUR_JWT_MINIMALE = 32;

    /**
     * Valeurs qu'un mot de passe ne doit jamais prendre.
     *
     * <p>Le nom du produit arrive en tête des listes utilisées par les outils d'attaque : il
     * est deviné avant même le premier mot du dictionnaire. C'est le mot de passe le plus
     * naturel à choisir, et le premier à tomber.
     */
    private static final List<String> MOTS_INTERDITS =
            List.of("skulvi", "admin", "password", "motdepasse", "changeme", "postgres", "test");

    private final StripeProperties stripe;
    private final String publicUrl;
    private final String corsOrigin;
    private final String adminPassword;
    private final String jwtSecret;
    private final boolean swagger;
    private final String llmProvider;
    private final String llmModel;
    private final String llmBaseUrl;

    public ConfigurationAudit(StripeProperties stripe,
                              @Value("${app.public-url:}") String publicUrl,
                              @Value("${app.security.cors-origin:}") String corsOrigin,
                              @Value("${app.security.admin-password:}") String adminPassword,
                              @Value("${app.security.jwt-secret:}") String jwtSecret,
                              @Value("${springdoc.swagger-ui.enabled:false}") boolean swagger,
                              @Value("${talent.llm.provider:}") String llmProvider,
                              @Value("${talent.llm.model:}") String llmModel,
                              @Value("${talent.llm.base-url:}") String llmBaseUrl) {
        this.stripe = stripe;
        this.publicUrl = publicUrl;
        this.corsOrigin = corsOrigin;
        this.adminPassword = adminPassword;
        this.jwtSecret = jwtSecret;
        this.swagger = swagger;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.llmBaseUrl = llmBaseUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifier() {
        boolean local = estLocal(publicUrl);
        List<String> alertes = new ArrayList<>();

        /*
         * L'instance se croit-elle en production ? On le déduit de PUBLIC_URL plutôt que d'un
         * profil Spring : c'est la seule variable dont la valeur est forcément juste, puisque
         * c'est l'adresse où les clients arrivent réellement.
         */
        if (!local) {
            if (swagger) {
                alertes.add("""
                        La documentation interactive de l'API est ACTIVE sur une instance \
                        publique. Elle décrit toutes les routes et tous les paramètres \
                        attendus. Posez SWAGGER_ENABLED=false.""");
            }
            if (corsOrigin.contains("localhost")) {
                alertes.add(("""
                        CORS_ORIGIN autorise encore « localhost » alors que le site est servi \
                        sur %s. Ne laissez que le vrai domaine.""").formatted(publicUrl));
            }
        }

        // Le cas le plus coûteux : un paiement actif avec une adresse de retour locale.
        if (stripe.usable() && local) {
            alertes.add("""
                        Le paiement est ACTIF mais PUBLIC_URL vaut « %s ». Après avoir payé, \
                        le client sera renvoyé sur sa propre machine et verra une page \
                        d'erreur, alors que le prélèvement aura bien eu lieu. Posez PUBLIC_URL \
                        sur le domaine public.""".formatted(publicUrl));
        }

        if (!adminPassword.isBlank() && adminPassword.length() < LONGUEUR_MOT_DE_PASSE_MINIMALE) {
            alertes.add(("""
                    ADMIN_PASSWORD ne fait que %d caractères. Ce compte voit tous les clients \
                    et toute la fréquentation du site : c'est la cible la plus intéressante de \
                    l'instance. Au moins %d caractères.""")
                    .formatted(adminPassword.length(), LONGUEUR_MOT_DE_PASSE_MINIMALE));
        }

        if (devinable(adminPassword)) {
            alertes.add("""
                    ADMIN_PASSWORD contient le nom du produit ou un mot courant. Les outils \
                    d'attaque essaient le nom du service AVANT le premier mot du dictionnaire : \
                    c'est le mot de passe le plus naturel à choisir, et le premier à tomber.""");
        }

        if (jwtSecret.length() < LONGUEUR_JWT_MINIMALE) {
            alertes.add(("""
                    JWT_SECRET ne fait que %d caractères. Une clé courte se retrouve par force \
                    brute, et qui la possède peut forger le jeton de n'importe quel compte, \
                    administrateur compris.""").formatted(jwtSecret.length()));
        }

        /*
         * Variables définies mais vides.
         *
         * <p>Le piège le plus discret de toute la configuration. Dans « ${VAR:defaut} », la
         * valeur de repli ne s'applique que si la variable est ABSENTE. Une ligne « VAR= »
         * laissée dans le fichier la définit à la chaîne vide, ce qui est une valeur comme une
         * autre : le repli est ignoré, et le réglage devient vide au lieu de prendre son
         * défaut. Rien ne plante au démarrage, la panne apparaît à la première utilisation.
         */
        if (llmProvider.isBlank()) {
            alertes.add("""
                    talent.llm.provider est vide. Une ligne « LLM_PROVIDER= » sans valeur dans \
                    le fichier d'environnement n'active PAS le défaut : elle définit la variable \
                    à la chaîne vide. Retirez la ligne, ou donnez-lui une valeur.""");
        }

        if ("ollama".equals(llmProvider)) {
            if (llmModel.isBlank()) {
                alertes.add("""
                        talent.llm.model est vide : aucune analyse de CV ne pourra aboutir. Une \
                        ligne « OLLAMA_MODEL= » sans valeur annule le défaut au lieu de \
                        l'activer. Retirez la ligne, ou posez OLLAMA_MODEL=qwen2.5:3b.""");
            }
            if (llmBaseUrl.isBlank()) {
                alertes.add("""
                        talent.llm.base-url est vide : le service d'analyse est injoignable. \
                        Dans un conteneur, cette valeur vient du bloc « environment » du \
                        compose (http://ollama:11434), pas du fichier .env.""");
            }
        }

        /*
         * Les réglages effectifs sont journalisés dans tous les cas, alerte ou non.
         *
         * <p>C'est la seule façon de savoir ce que l'application a réellement lu, par
         * opposition à ce que le fichier .env semble dire : le bloc « environment » du
         * compose, les variables du système et le fichier se superposent selon un ordre de
         * priorité qu'on ne devine pas en lisant un seul des trois. Aucune valeur secrète
         * n'apparaît, seulement des longueurs.
         */
        log.info("Réglages effectifs : public-url={} · cors={} · swagger={} · llm={}/{} sur {} "
                        + "· paiement={} · admin-password={} car. · jwt-secret={} car.",
                publicUrl.isBlank() ? "(vide)" : publicUrl,
                corsOrigin.isBlank() ? "(vide)" : corsOrigin,
                swagger ? "ACTIF" : "coupé",
                llmProvider.isBlank() ? "(vide)" : llmProvider,
                llmModel.isBlank() ? "(vide)" : llmModel,
                llmBaseUrl.isBlank() ? "(vide)" : llmBaseUrl,
                stripe.usable() ? "actif" : "inactif",
                adminPassword.length(), jwtSecret.length());

        if (alertes.isEmpty()) {
            log.info("Configuration vérifiée : aucune alerte ({}).", local ? "instance locale" : publicUrl);
            return;
        }

        log.warn("");
        log.warn("┌─ CONFIGURATION : {} point(s) à corriger ─────────────────", alertes.size());
        for (String alerte : alertes) {
            log.warn("│");
            log.warn("│  • {}", alerte.replace("\n", " ").replaceAll(" {2,}", " ").trim());
        }
        log.warn("│");
        log.warn("└──────────────────────────────────────────────────────────");
        log.warn("");
    }

    /**
     * L'adresse publique est-elle une adresse de développement ?
     *
     * <p>Une adresse vide compte comme locale : c'est le cas d'une instance lancée sans aucune
     * configuration, et traiter l'inconnu comme de la production produirait des alertes à
     * chaque démarrage en local.
     */
    static boolean estLocal(String url) {
        if (url == null || url.isBlank()) return true;
        String bas = url.toLowerCase();
        return bas.contains("localhost") || bas.contains("127.0.0.1") || bas.contains("0.0.0.0");
    }

    /**
     * Le mot de passe contient-il un mot que les outils d'attaque essaient en premier ?
     *
     * <p>Comparaison en minuscules et par inclusion : « Skulvi2024 » est aussi mauvais que
     * « skulvi ». Un mot de passe vide n'est pas signalé ici — il signifie que le compte
     * d'administration n'est tout simplement pas créé, ce qui est une configuration valide.
     */
    static boolean devinable(String motDePasse) {
        if (motDePasse == null || motDePasse.isBlank()) return false;
        String bas = motDePasse.toLowerCase();
        return MOTS_INTERDITS.stream().anyMatch(bas::contains);
    }
}
