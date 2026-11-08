package org.example.skulvi_cv.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.analytics.AnalyticsDtos.AnalyticsOverview;
import org.example.skulvi_cv.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistiques de fréquentation de l'organisation connectée.
 *
 * <p>Le périmètre vient du jeton : un recruteur voit les visites de <b>ses</b> pages carrière
 * et de <b>ses</b> offres, pas celles des autres clients. Aucun paramètre ne permet d'élargir
 * ce périmètre.
 */
@RestController
@Tag(name = "Statistiques", description = "Fréquentation et conversion")
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final CurrentUser current;

    @Operation(summary = "Fréquentation de mes pages",
            description = "Série journalière, visiteurs distincts, provenances et entonnoir de candidature.")
    @GetMapping("/overview")
    public AnalyticsOverview overview(@RequestParam(defaultValue = "30") int days) {
        return analytics.overview(current.orgId(), days);
    }
}
