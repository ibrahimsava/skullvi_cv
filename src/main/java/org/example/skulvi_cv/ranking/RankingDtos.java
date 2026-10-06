package org.example.skulvi_cv.ranking;

import org.example.skulvi_cv.scoring.Priority;

import java.util.UUID;

public final class RankingDtos {

    private RankingDtos() {}

    public record RankedCandidate(int rank, UUID applicationId, String candidateName, String email, int score, Priority priority) {}

    public record DashboardStats(long offers, long applications, long scored, long failed, long highPriority, Double averageScore) {}
}
