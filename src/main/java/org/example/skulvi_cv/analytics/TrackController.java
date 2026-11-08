package org.example.skulvi_cv.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analytics.AnalyticsDtos.TrackRequest;
import org.example.skulvi_cv.common.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Réception des signalements de visite.
 *
 * <p>Route publique : les visiteurs qu'on cherche justement à compter ne sont pas connectés.
 * Deux garde-fous en conséquence :
 *
 * <ul>
 *   <li>une limite de débit par adresse, pour qu'un script ne puisse pas gonfler les chiffres ;
 *   <li>rien de ce que le client envoie n'est cru sur parole — l'organisation est retrouvée
 *       côté serveur depuis l'offre ou l'identifiant d'entreprise, jamais acceptée telle quelle.
 * </ul>
 */
@RestController
@Tag(name = "Public", description = "Offres consultables sans compte")
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class TrackController {

    /** Généreux, mais suffisant pour écarter un script : 120 pages par quart d'heure. */
    private static final int MAX_EVENTS = 120;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final TrackingService tracking;
    private final RateLimiter rateLimiter;

    @Operation(summary = "Signaler une visite",
            description = """
                    Mesure d'audience sans cookie : le visiteur est réduit à une empreinte
                    quotidienne non réversible, et aucune adresse IP n'est conservée.""")
    @PostMapping("/track")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void track(@Valid @RequestBody TrackRequest request,
                      @Parameter(hidden = true) HttpServletRequest http) {
        rateLimiter.check("track:" + TrackingService.clientIp(http), MAX_EVENTS, WINDOW);
        tracking.record(request, http);
    }
}
