package org.example.skulvi_cv.ranking;

import org.example.skulvi_cv.scoring.Priority;

import java.util.UUID;

public final class RankingDtos {

    private RankingDtos() {}

    public record RankedCandidate(int rank,
                                  UUID applicationId,
                                  String candidateName,
                                  String email,
                                  int score,
                                  Priority priority) {}

    /**
     * Chiffres du tableau de bord, pour la seule organisation du jeton.
     *
     * <p>Les compteurs portaient auparavant sur toute la base : un nouveau client voyait les
     * candidatures de tous les autres dans ses statistiques.
     */
    public record DashboardStats(long offers,
                                 long openOffers,
                                 long applications,
                                 long scored,
                                 long failed,
                                 long highPriority,
                                 Double averageScore,
                                 long analysesPending,
                                 int analysesUsed,
                                 /** {@code null} si le plan ne plafonne pas les analyses. */
                                 Integer analysesLimit) {}
}
