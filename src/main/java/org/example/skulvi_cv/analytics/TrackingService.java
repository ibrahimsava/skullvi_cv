package org.example.skulvi_cv.analytics;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.analytics.AnalyticsDtos.TrackRequest;
import org.example.skulvi_cv.offer.Offer;
import org.example.skulvi_cv.offer.OfferRepository;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/** Enregistre les visites. Toute erreur est avalée : la mesure ne doit jamais casser une page. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final VisitEventRepository events;
    private final VisitorFingerprint fingerprint;
    private final OfferRepository offers;
    private final OrganizationRepository organizations;

    /**
     * Enregistre une visite.
     *
     * <p>Jamais d'exception vers l'appelant : une statistique perdue est sans conséquence,
     * une page blanche parce que le compteur a échoué serait inacceptable.
     */
    @Transactional
    public void record(TrackRequest req, HttpServletRequest http) {
        try {
            LocalDate day = LocalDate.now();

            VisitEvent event = new VisitEvent();
            event.setKind(req.kind() == null ? VisitKind.PAGE : req.kind());
            event.setPath(normalizePath(req.path()));
            event.setVisitorHash(fingerprint.of(clientIp(http), http.getHeader("User-Agent"), day));
            event.setReferrerHost(hostOf(req.referrer()));
            event.setDevice(DeviceKind.fromUserAgent(http.getHeader("User-Agent")));
            event.setOrganizationId(resolveOrganization(req));
            event.setDay(day);

            events.save(event);

        } catch (RuntimeException e) {
            log.debug("Visite non enregistrée : {}", e.getMessage());
        }
    }

    /** Enregistre un événement serveur (inscription, candidature envoyée). */
    @Transactional
    public void recordServerSide(VisitKind kind, UUID organizationId, String path) {
        try {
            LocalDate day = LocalDate.now();

            VisitEvent event = new VisitEvent();
            event.setKind(kind);
            event.setPath(normalizePath(path));
            // Pas de requête HTTP sous la main : l'empreinte est aléatoire et unique, ce qui
            // est correct puisque ces événements se comptent, ils ne dédoublonnent pas.
            event.setVisitorHash(fingerprint.of(UUID.randomUUID().toString(), kind.name(), day));
            event.setDevice(DeviceKind.UNKNOWN);
            event.setOrganizationId(organizationId);
            event.setDay(day);

            events.save(event);

        } catch (RuntimeException e) {
            log.debug("Événement {} non enregistré : {}", kind, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- rattachement

    /**
     * À quelle organisation rattacher la visite.
     *
     * <p>Déduit du serveur, jamais accepté du client : laisser le navigateur désigner
     * l'organisation permettrait de gonfler les statistiques de n'importe qui.
     */
    private UUID resolveOrganization(TrackRequest req) {
        if (req.offerId() != null) {
            return offers.findWithOrganization(req.offerId())
                    .map(Offer::getOrganization)
                    .map(o -> o.getId())
                    .orElse(null);
        }
        if (req.orgSlug() != null && !req.orgSlug().isBlank()) {
            return organizations.findBySlug(req.orgSlug().trim())
                    .map(o -> o.getId())
                    .orElse(null);
        }
        return null;
    }

    // ---------------------------------------------------------------- nettoyage

    /**
     * Normalise le chemin visité.
     *
     * <p>Les paramètres de requête sont retirés, et les identifiants remplacés par un
     * marqueur. Sans cela, chaque offre créerait sa propre ligne et la liste des pages les
     * plus vues serait illisible — en plus de laisser traîner des jetons éventuellement
     * présents dans une URL.
     */
    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String path = raw.split("[?#]")[0].trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // Remplace tout segment ressemblant à un identifiant (UUID ou nombre long).
        path = path.replaceAll(
                "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/:id");
        path = path.replaceAll("/\\d{4,}", "/:id");

        return path.length() > 160 ? path.substring(0, 160) : path;
    }

    /** Ne garde que le domaine de provenance : l'URL complète pourrait être identifiante. */
    static String hostOf(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase().replaceFirst("^www\\.", "");
            return host.length() > 120 ? host.substring(0, 120) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Adresse du client.
     *
     * <p>Derrière un reverse proxy, {@code getRemoteAddr()} renvoie l'adresse du proxy : tous
     * les visiteurs auraient alors la même empreinte et le compte de visiteurs distincts
     * vaudrait un.
     */
    static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
