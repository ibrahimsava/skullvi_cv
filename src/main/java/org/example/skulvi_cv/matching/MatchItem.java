package org.example.skulvi_cv.matching;

import org.example.skulvi_cv.offer.CriterionType;
import org.example.skulvi_cv.offer.OfferCriterion;

/** Résultat de l'évaluation d'un critère : sert directement à expliquer le score. */
public record MatchItem(
        String criterion,
        CriterionType type,
        boolean mandatory,
        int weight,
        double ratio,
        boolean matched,
        double points,
        String evidence) {

    public static MatchItem of(OfferCriterion c, double ratio, boolean matched, String evidence) {
        double points = Math.round(c.getWeight() * ratio * 10.0) / 10.0;
        return new MatchItem(c.getName(), c.getType(), c.isMandatory(), c.getWeight(), ratio, matched, points, evidence);
    }
}
