package org.example.skulvi_cv.ranking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.ranking.RankingDtos.*;
import org.example.skulvi_cv.scoring.Priority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Classement", description = "Classement des candidats et statistiques")
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService service;

    @Operation(summary = "Classement des candidats d'une offre",
            description = "Filtres optionnels : minScore et priority.")
    @GetMapping("/offers/{offerId}/ranking")
    public List<RankedCandidate> ranking(@PathVariable UUID offerId,
                                         @RequestParam(required = false) Integer minScore,
                                         @RequestParam(required = false) Priority priority) {
        return service.ranking(offerId, minScore, priority);
    }

    @Operation(summary = "Statistiques du tableau de bord")
    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return service.stats();
    }
}