package org.example.skulvi_cv.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    /**
     * Signalement d'une visite, envoyé par le navigateur.
     *
     * <p>L'organisation est déduite côté serveur du chemin ou du jeton : l'accepter du client
     * permettrait de gonfler les statistiques d'un concurrent, ou de polluer les siennes.
     */
    public record TrackRequest(
            @NotBlank @Size(max = 200) String path,
            @Size(max = 300) String referrer,
            VisitKind kind,
            /** Offre consultée, quand la page en concerne une. */
            UUID offerId,
            /** Entreprise visitée, pour une page carrière. */
            @Size(max = 60) String orgSlug) {}

    /** Un point de la série temporelle. */
    public record DayPoint(LocalDate day, long visits, long visitors) {}

    /** Une part d'une répartition : libellé, compte, pourcentage déjà calculé. */
    public record Slice(String label, long count, double share) {}

    /**
     * Entonnoir de recrutement : de la vue d'offre à la candidature envoyée.
     *
     * <p>Les taux sont calculés côté serveur pour que tous les écrans affichent le même
     * chiffre, et pour éviter une division par zéro recopiée dans chaque composant.
     */
    public record Funnel(
            long offerViews,
            long applyStarted,
            long applySent,
            double startRate,
            double completionRate,
            double overallRate) {}

    /**
     * Un indicateur et sa série journalière : de quoi dessiner un « petit multiple ».
     *
     * <p>Une vignette par indicateur, toutes alignées sur le même axe de temps : c'est ce qui
     * permet de comparer des grandeurs d'ordres très différents — mille visites et dix-sept
     * inscriptions — sans écraser la plus petite. Un seul grand graphique ne le permet pas.
     *
     * @param key    identifiant stable, pour la couleur et l'ordre d'affichage
     * @param label  libellé affiché
     * @param total  total sur la période
     * @param trend  variation en pourcentage, ou {@code null} si la période précédente est vide
     * @param daily  un point par jour de la période, aligné sur {@code series}, zéros compris
     */
    public record Metric(String key, String label, long total, Double trend, List<Long> daily) {}

    /** Tout ce qu'affiche la page de statistiques, en un seul appel. */
    public record AnalyticsOverview(
            int days,
            LocalDate from,
            LocalDate to,
            long visits,
            long visitors,
            long visitsPrevious,
            long visitorsPrevious,
            /** Variation en pourcentage par rapport à la période précédente. */
            Double visitsTrend,
            Double visitorsTrend,
            List<DayPoint> series,
            /** Un indicateur par vignette, tous alignés sur le même axe de temps. */
            List<Metric> metrics,
            List<Slice> topPages,
            List<Slice> referrers,
            List<Slice> devices,
            Funnel funnel,
            /** Inscriptions sur la période : candidats, et entreprises pour la plateforme. */
            long candidateSignups,
            long organizationSignups) {}
}
