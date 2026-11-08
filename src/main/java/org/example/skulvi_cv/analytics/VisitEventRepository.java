package org.example.skulvi_cv.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Agrégations de la mesure d'audience.
 *
 * <p>Tout est calculé en SQL et jamais en Java : ramener des milliers d'événements pour les
 * compter en mémoire ferait tomber la page de statistiques dès les premiers milliers de
 * visites. Chaque requête existe en deux versions, avec et sans organisation — le recruteur
 * ne voit que ses propres pages, l'administrateur de la plateforme voit tout.
 */
public interface VisitEventRepository extends JpaRepository<VisitEvent, UUID> {

    /** Une ligne de série temporelle : jour, visites, visiteurs distincts. */
    interface DailyRow {
        LocalDate getDay();
        long getVisits();
        long getVisitors();
    }

    /** Un libellé et son compte : pages les plus vues, provenances, appareils. */
    interface CountRow {
        String getLabel();
        long getTotal();
    }

    // ---------------------------------------------------------------- séries

    @Query("""
            select v.day as day, count(v) as visits, count(distinct v.visitorHash) as visitors
            from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.day
            order by v.day asc
            """)
    List<DailyRow> dailyAll(@Param("since") LocalDate since);

    @Query("""
            select v.day as day, count(v) as visits, count(distinct v.visitorHash) as visitors
            from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.day
            order by v.day asc
            """)
    List<DailyRow> dailyForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    // ---------------------------------------------------------------- totaux

    @Query("""
            select count(v) from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            """)
    long countVisits(@Param("since") LocalDate since);

    @Query("""
            select count(distinct v.visitorHash) from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            """)
    long countVisitors(@Param("since") LocalDate since);

    @Query("""
            select count(v) from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            """)
    long countVisitsForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    @Query("""
            select count(distinct v.visitorHash) from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            """)
    long countVisitorsForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    long countByKindAndDayGreaterThanEqual(VisitKind kind, LocalDate since);

    long countByOrganizationIdAndKindAndDayGreaterThanEqual(UUID organizationId, VisitKind kind, LocalDate since);

    // ---------------------------------------------------------------- répartitions

    @Query("""
            select v.path as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.path
            order by count(v) desc
            limit 8
            """)
    List<CountRow> topPaths(@Param("since") LocalDate since);

    @Query("""
            select v.path as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.path
            order by count(v) desc
            limit 8
            """)
    List<CountRow> topPathsForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    @Query("""
            select coalesce(v.referrerHost, 'Accès direct') as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.referrerHost
            order by count(v) desc
            limit 6
            """)
    List<CountRow> topReferrers(@Param("since") LocalDate since);

    @Query("""
            select coalesce(v.referrerHost, 'Accès direct') as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.referrerHost
            order by count(v) desc
            limit 6
            """)
    List<CountRow> topReferrersForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    @Query("""
            select cast(v.device as string) as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.device
            """)
    List<CountRow> deviceSplit(@Param("since") LocalDate since);

    @Query("""
            select cast(v.device as string) as label, count(v) as total
            from VisitEvent v
            where v.day >= :since and v.organizationId = :org
              and v.kind = org.example.skulvi_cv.analytics.VisitKind.PAGE
            group by v.device
            """)
    List<CountRow> deviceSplitForOrg(@Param("org") UUID organizationId, @Param("since") LocalDate since);

    // ---------------------------------------------------------------- purge

    /**
     * Supprime les événements trop anciens.
     *
     * <p>Une mesure d'audience n'a aucune raison d'être conservée indéfiniment : passé un an,
     * la donnée ne sert plus à rien et devient un stock à protéger pour rien.
     */
    @Modifying
    @Query("delete from VisitEvent v where v.day < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
