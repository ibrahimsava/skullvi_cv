package org.example.skulvi_cv.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Empreinte quotidienne d'un visiteur, non réversible et non durable.
 *
 * <p>Trois propriétés recherchées, dans cet ordre :
 *
 * <ol>
 *   <li><b>Non réversible</b> : l'empreinte vient d'un SHA-256 sur l'IP, le navigateur et un
 *       sel secret. Impossible de retrouver l'adresse IP à partir de ce qui est stocké.
 *   <li><b>Non attaquable par dictionnaire</b> : sans le sel, un adversaire pourrait hacher les
 *       quatre milliards d'adresses IPv4 et retrouver celle d'un visiteur en comparant. Le sel
 *       aléatoire, jamais écrit en base, rend ce calcul inutile.
 *   <li><b>Non durable</b> : le sel du jour inclut la date. Deux visites du même navigateur à
 *       deux jours d'écart donnent deux empreintes sans lien. On compte donc les visiteurs
 *       distincts d'une journée, sans jamais pouvoir suivre quelqu'un dans le temps.
 * </ol>
 *
 * <p>Le sel est tiré au démarrage et vit en mémoire. Un redémarrage du serveur change le sel
 * et peut donc recompter un visiteur dans la même journée : c'est une imprécision assumée,
 * préférable à un secret persisté qu'il faudrait protéger.
 */
@Slf4j
@Component
public class VisitorFingerprint {

    private final String secret;

    public VisitorFingerprint() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.secret = HexFormat.of().formatHex(random);
        log.info("Sel de mesure d'audience généré (en mémoire, renouvelé à chaque démarrage)");
    }

    /**
     * Calcule l'empreinte du visiteur pour la journée donnée.
     *
     * @param ip        adresse du client, utilisée mais jamais stockée
     * @param userAgent en-tête du navigateur, utilisé mais jamais stocké
     */
    public String of(String ip, String userAgent, LocalDate day) {
        String material = day + "|" + secret + "|" + nullToEmpty(ip) + "|" + nullToEmpty(userAgent);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la plateforme : si elle manque, l'environnement est cassé.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
