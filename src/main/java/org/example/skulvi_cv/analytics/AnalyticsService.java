package org.example.skulvi_cv.analytics;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.Utilisateur.AppUserRepository;
import org.example.skulvi_cv.analytics.AnalyticsDtos.AnalyticsOverview;
import org.example.skulvi_cv.analytics.AnalyticsDtos.DayPoint;
import org.example.skulvi_cv.analytics.AnalyticsDtos.Funnel;
import org.example.skulvi_cv.analytics.AnalyticsDtos.Slice;
import org.example.skulvi_cv.analytics.VisitEventRepository.CountRow;
import org.example.skulvi_cv.analytics.VisitEventRepository.DailyRow;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcule les tableaux de statistiques.
 *
 * <p>Un seul appel renvoie tout ce qu'affiche l'écran : série temporelle, totaux, variations,
 * répartitions et entonnoir. Multiplier les requêtes depuis le navigateur donnerait une page
 * qui se remplit par morceaux, et autant d'occasions d'afficher des chiffres incohérents
 * entre eux parce que calculés à des instants différents.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** Durée de conservation de la mesure d'audience. */
    private static final int RETENTION_DAYS = 400;

    private final VisitEventRepository events;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;

    /**
     * Statistiques d'une organisation : ses pages carrière, ses offres, ses candidatures.
     *
     * @param orgId organisation concernée, ou {@code null} pour l'ensemble de la plateforme
     */
    @Transactional(readOnly = true)
    public AnalyticsOverview overview(UUID orgId, int days) {
        int span = Math.min(365, Math.max(7, days));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);
        LocalDate previousFrom = from.minusDays(span);

        long visits = orgId == null
                ? events.countVisits(from)
                : events.countVisitsForOrg(orgId, from);
        long visitors = orgId == null
                ? events.countVisitors(from)
                : events.countVisitorsForOrg(orgId, from);

        // Période précédente, de même longueur, pour afficher une variation.
        long visitsBefore = (orgId == null
                ? events.countVisits(previousFrom)
                : events.countVisitsForOrg(orgId, previousFrom)) - visits;
        long visitorsBefore = (orgId == null
                ? events.countVisitors(previousFrom)
                : events.countVisitorsForOrg(orgId, previousFrom)) - visitors;

        List<DailyRow> rows = orgId == null
                ? events.dailyAll(from)
                : events.dailyForOrg(orgId, from);

        return new AnalyticsOverview(
                span,
                from,
                today,
                visits,
                visitors,
                Math.max(0, visitsBefore),
                Math.max(0, visitorsBefore),
                trend(visits, Math.max(0, visitsBefore)),
                trend(visitors, Math.max(0, visitorsBefore)),
                fillGaps(rows, from, today),
                slices(orgId == null ? events.topPaths(from) : events.topPathsForOrg(orgId, from)),
                slices(orgId == null ? events.topReferrers(from) : events.topReferrersForOrg(orgId, from)),
                slices(deviceLabels(orgId == null ? events.deviceSplit(from) : events.deviceSplitForOrg(orgId, from))),
                funnel(orgId, from),
                countKind(orgId, VisitKind.SIGNUP_CANDIDATE, from),
                countKind(orgId, VisitKind.SIGNUP_ORG, from));
    }

    /** Totaux de la plateforme, affichés à l'administrateur Skulvi. */
    @Transactional(readOnly = true)
    public Map<String, Long> platformTotals() {
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        return Map.of(
                "organizations", organizations.count(),
                "organizationsLast30", organizations.countByCreatedAtAfter(monthAgo),
                "users", users.count());
    }

    // ---------------------------------------------------------------- calculs

    /**
     * Complète les jours sans visite.
     *
     * <p>SQL ne renvoie que les jours où il s'est passé quelque chose. Tracer directement ces
     * lignes donnerait une courbe qui relie le 3 au 9 en ligne droite, laissant croire à une
     * activité continue là où il n'y en a eu aucune.
     */
    private static List<DayPoint> fillGaps(List<DailyRow> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, DailyRow> byDay = rows.stream()
                .collect(Collectors.toMap(DailyRow::getDay, Function.identity(), (a, b) -> a));

        List<DayPoint> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyRow row = byDay.get(d);
            series.add(new DayPoint(d, row == null ? 0 : row.getVisits(), row == null ? 0 : row.getVisitors()));
        }
        return series;
    }

    private static List<Slice> slices(List<CountRow> rows) {
        long total = rows.stream().mapToLong(CountRow::getTotal).sum();
        return rows.stream()
                .map(r -> new Slice(r.getLabel(), r.getTotal(),
                        total == 0 ? 0 : round(r.getTotal() * 100.0 / total)))
                .toList();
    }

    /** Traduit les noms d'enum d'appareil en libellés affichables. */
    private static List<CountRow> deviceLabels(List<CountRow> rows) {
        return rows.stream().<CountRow>map(r -> new CountRow() {
            @Override public String getLabel() {
                return switch (r.getLabel()) {
                    case "MOBILE" -> "Mobile";
                    case "DESKTOP" -> "Ordinateur";
                    default -> "Indéterminé";
                };
            }
            @Override public long getTotal() { return r.getTotal(); }
        }).toList();
    }

    private Funnel funnel(UUID orgId, LocalDate from) {
        long views = countKind(orgId, VisitKind.OFFER_VIEW, from);
        long started = countKind(orgId, VisitKind.APPLY_START, from);
        long sent = countKind(orgId, VisitKind.APPLY_DONE, from);

        return new Funnel(views, started, sent,
                rate(started, views),
                rate(sent, started),
                rate(sent, views));
    }

    private long countKind(UUID orgId, VisitKind kind, LocalDate from) {
        return orgId == null
                ? events.countByKindAndDayGreaterThanEqual(kind, from)
                : events.countByOrganizationIdAndKindAndDayGreaterThanEqual(orgId, kind, from);
    }

    /** Taux en pourcentage, zéro si le dénominateur est nul. */
    private static double rate(long part, long whole) {
        return whole == 0 ? 0 : round(part * 100.0 / whole);
    }

    /**
     * Variation entre deux périodes.
     *
     * <p>{@code null} quand la période précédente est vide : afficher « +100 % » parce qu'on
     * passe de zéro à une visite serait trompeur, mieux vaut ne rien afficher.
     */
    private static Double trend(long now, long before) {
        if (before == 0) {
            return null;
        }
        return round((now - before) * 100.0 / before);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ---------------------------------------------------------------- purge

    /**
     * Efface la mesure d'audience devenue inutile.
     *
     * <p>Passé un peu plus d'un an, la donnée ne sert plus à comparer et devient un stock à
     * protéger sans raison. La supprimer est la seule façon de ne pas avoir à la sécuriser.
     */
    @Scheduled(cron = "${talent.analytics.purge-cron:0 30 4 * * *}")
    @Transactional
    public void purgeOldEvents() {
        events.deleteOlderThan(LocalDate.now().minusDays(RETENTION_DAYS));
    }
}
