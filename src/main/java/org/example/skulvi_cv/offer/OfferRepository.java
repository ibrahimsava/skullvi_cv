package org.example.skulvi_cv.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Toutes les lectures passent par l'identifiant d'organisation.
 *
 * <p>Il n'y a volontairement <b>aucune</b> méthode qui renvoie une offre sur son seul
 * identifiant : c'est la garantie structurelle qu'un appelant distrait ne peut pas servir
 * l'offre d'un autre client. {@code findById}, hérité de {@code JpaRepository}, n'est utilisé
 * que par les routes publiques, qui n'exposent que des offres ouvertes.
 */
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Offer> findByOrganizationIdOrderByStatusAscTitleAsc(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, OfferStatus status);

    long countByOrganizationId(UUID organizationId);

    /**
     * Offres ouvertes avec leur organisation déjà chargée.
     *
     * <p>Le {@code join fetch} évite un N+1 : la vue publique affiche le nom de l'entreprise de
     * chaque offre, ce qui déclencherait sinon une requête par ligne de la liste.
     */
    @Query("select o from Offer o join fetch o.organization where o.status = :status")
    List<Offer> findPublicByStatus(@Param("status") OfferStatus status);

    @Query("select o from Offer o join fetch o.organization " +
            "where o.organization.slug = :slug and o.status = :status")
    List<Offer> findPublicBySlugAndStatus(@Param("slug") String slug, @Param("status") OfferStatus status);

    @Query("select o from Offer o join fetch o.organization where o.id = :id")
    Optional<Offer> findWithOrganization(@Param("id") UUID id);
}
