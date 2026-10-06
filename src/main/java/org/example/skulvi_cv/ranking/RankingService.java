package org.example.skulvi_cv.ranking;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.Application;
import org.example.skulvi_cv.application.ApplicationRepository;
import org.example.skulvi_cv.application.ApplicationStatus;
import org.example.skulvi_cv.offer.OfferRepository;
import org.example.skulvi_cv.offer.OfferService;
import org.example.skulvi_cv.ranking.RankingDtos.*;
import org.example.skulvi_cv.scoring.Priority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ApplicationRepository applications;
    private final OfferRepository offerRepository;
    private final OfferService offers;

    @Transactional(readOnly = true)
    public List<RankedCandidate> ranking(UUID offerId, Integer minScore, Priority priority) {
        offers.getEntity(offerId);
        List<Application> scored = applications.findByOfferIdAndStatusOrderByScoreTotalDesc(offerId, ApplicationStatus.SCORED);
        List<RankedCandidate> result = new ArrayList<>();
        for (Application a : scored) {
            if (minScore != null && a.getScoreTotal() < minScore) continue;
            if (priority != null && a.getPriority() != priority) continue;
            var c = a.getCandidate();
            result.add(new RankedCandidate(result.size() + 1, a.getId(), c.getFirstName() + " " + c.getLastName(),
                    c.getEmail(), a.getScoreTotal(), a.getPriority()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        return new DashboardStats(
                offerRepository.count(),
                applications.count(),
                applications.countByStatus(ApplicationStatus.SCORED),
                applications.countByStatus(ApplicationStatus.ANALYSIS_FAILED),
                applications.countByPriority(Priority.HIGH),
                applications.averageScore());
    }
}
