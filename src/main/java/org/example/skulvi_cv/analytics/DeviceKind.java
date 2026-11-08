package org.example.skulvi_cv.analytics;

/**
 * Famille d'appareil, déduite grossièrement de l'en-tête du navigateur.
 *
 * <p>Volontairement limitée à trois valeurs. Une détection fine du modèle de téléphone
 * rapprocherait la mesure d'une empreinte de navigateur, ce qui la rendrait identifiante —
 * et ferait tomber l'exemption de consentement.
 */
public enum DeviceKind {
    MOBILE,
    DESKTOP,
    UNKNOWN;

    public static DeviceKind fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase();
        boolean mobile = ua.contains("mobi") || ua.contains("android")
                || ua.contains("iphone") || ua.contains("ipad");
        return mobile ? MOBILE : DESKTOP;
    }
}
