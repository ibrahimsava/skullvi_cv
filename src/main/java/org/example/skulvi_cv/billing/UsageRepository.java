package org.example.skulvi_cv.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsageRepository extends JpaRepository<UsageRecord, UUID> {

    Optional<UsageRecord> findByOrganizationIdAndPeriodAndKind(UUID organizationId, String period, UsageKind kind);

    /**
     * Incrémente le compteur du mois, en le créant s'il n'existe pas encore.
     *
     * <p>Un seul {@code INSERT ... ON CONFLICT} plutôt qu'un « je lis, je crée si absent,
     * j'incrémente » en trois temps. Deux raisons :
     *
     * <ul>
     *   <li>deux analyses qui se terminent en même temps liraient sinon la même valeur, et une
     *       des deux consommations serait perdue — autant d'argent non facturé ;
     *   <li>rattraper la violation de contrainte unique en Java condamnerait la transaction
     *       courante sous PostgreSQL : tout ce qui suivrait dans le même appel échouerait.
     * </ul>
     *
     * <p>{@code kind} est passé en texte parce que la colonne stocke le nom de l'enum.
     */
    @Modifying
    @Query(value = """
            insert into usage_record (id, organization_id, period, kind, used)
            values (gen_random_uuid(), :org, :period, :kind, 1)
            on conflict (organization_id, period, kind)
            do update set used = usage_record.used + 1
            """, nativeQuery = true)
    void recordOne(@Param("org") UUID organizationId,
                   @Param("period") String period,
                   @Param("kind") String kind);
}
