package org.example.skulvi_cv.scoring;

import org.example.skulvi_cv.matching.MatchItem;

import java.util.List;

/** total = score affiché (après plafonnement éventuel), rawTotal = score avant plafonnement. */
public record ScoreResult(
        int total,
        int rawTotal,
        boolean capped,
        Priority priority,
        List<MatchItem> items)
       {}
