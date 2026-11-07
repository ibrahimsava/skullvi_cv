package org.example.skulvi_cv.offer;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.application.ApplicationRepository;
import org.example.skulvi_cv.billing.QuotaService;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.offer.OfferDtos.*;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository repository;
    private final OrganizationRepository organizations;
    private final ApplicationRepository applications;
    private final QuotaService quotas;

    // ---------------------------------------------------------------- espace recruteur

    @Transactional
    public OfferResponse create(UUID orgId, OfferRequest r) {
        quotas.assertCanCreateOffer(orgId);

        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + orgId));

        Offer o = new Offer();
        o.setOrganization(org);
        o.setTitle(r.title());
        o.setDescription(r.description());
        o.setDomain(r.domain());
        o.setLevel(r.level());
        o.setMinExperienceYears(r.minExperienceYears());
        o.setStartDate(r.startDate());
        o.setClosingDate(r.closingDate());
        o.setStatus(OfferStatus.OPEN);
        for (CriterionRequest cr : r.criteria()) {
            OfferCriterion c = new OfferCriterion();
            c.setOffer(o);
            c.setName(cr.name().trim());
            c.setType(cr.type());
            c.setMandatory(cr.mandatory());
            c.setWeight(cr.weight());
            c.setThreshold(cr.threshold());
            o.getCriteria().add(c);
        }
        return toResponse(repository.save(o), 0);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> list(UUID orgId) {
        return repository.findByOrganizationIdOrderByStatusAscTitleAsc(orgId).stream()
                .map(o -> toResponse(o, applications.countByOfferIdAndOrganizationId(o.getId(), orgId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public OfferResponse get(UUID orgId, UUID id) {
        Offer o = getEntity(orgId, id);
        return toResponse(o, applications.countByOfferIdAndOrganizationId(id, orgId));
    }

    @Transactional
    public OfferResponse close(UUID orgId, UUID id) {
        Offer o = getEntity(orgId, id);
        o.setStatus(OfferStatus.CLOSED);
        return toResponse(repository.save(o), 0);
    }

    @Transactional
    public OfferResponse reopen(UUID orgId, UUID id) {
        quotas.assertCanCreateOffer(orgId);
        Offer o = getEntity(orgId, id);
        o.setStatus(OfferStatus.OPEN);
        return toResponse(repository.save(o), 0);
    }

    /**
     * Charge une offre appartenant à l'organisation donnée.
     *
     * <p>404 et non 403 quand l'offre existe mais appartient à un autre client : répondre
     * « interdit » confirmerait son existence, et permettrait d'énumérer les offres de la
     * concurrence en comparant les codes de retour.
     */
    public Offer getEntity(UUID orgId, UUID id) {
        return repository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Offre introuvable : " + id));
    }

    // ---------------------------------------------------------------- vue publique

    /** Toutes les offres ouvertes, toutes organisations confondues : le job board public. */
    @Transactional(readOnly = true)
    public List<PublicOfferResponse> listPublic() {
        return repository.findPublicByStatus(OfferStatus.OPEN).stream()
                .filter(OfferService::notExpired)
                .sorted(Comparator.comparing(Offer::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(OfferService::toPublic)
                .toList();
    }

    /** Les offres ouvertes d'une seule organisation : sa page carrière. */
    @Transactional(readOnly = true)
    public List<PublicOfferResponse> listPublicBySlug(String slug) {
        if (!organizations.existsBySlug(slug)) {
            throw ApiException.notFound("Entreprise introuvable : " + slug);
        }
        return repository.findPublicBySlugAndStatus(slug, OfferStatus.OPEN).stream()
                .filter(OfferService::notExpired)
                .sorted(Comparator.comparing(Offer::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(OfferService::toPublic)
                .toList();
    }

    /** Détail public d'une offre. Accessible sans compte, c'est la page sur laquelle on postule. */
    @Transactional(readOnly = true)
    public PublicOfferResponse getPublic(UUID id) {
        return toPublic(requireWithOrganization(id));
    }

    /** Charge une offre pour un dépôt de candidature public, en vérifiant qu'elle accepte encore. */
    @Transactional(readOnly = true)
    public Offer getOpenForApplication(UUID id) {
        Offer o = requireWithOrganization(id);
        if (o.getStatus() != OfferStatus.OPEN || !notExpired(o)) {
            throw ApiException.conflict("Cette offre est fermée");
        }
        return o;
    }

    private Offer requireWithOrganization(UUID id) {
        return repository.findWithOrganization(id)
                .orElseThrow(() -> ApiException.notFound("Offre introuvable : " + id));
    }

    /** Une offre peut être OPEN en base mais avoir dépassé sa date de clôture. */
    private static boolean notExpired(Offer o) {
        return o.getClosingDate() == null || !o.getClosingDate().isBefore(LocalDate.now());
    }

    // ---------------------------------------------------------------- conversions

    static OfferResponse toResponse(Offer o, long applicationCount) {
        List<CriterionResponse> criteria = o.getCriteria().stream()
                .map(c -> new CriterionResponse(c.getId(),
                        c.getName(),
                        c.getType(),
                        c.isMandatory(),
                        c.getWeight(),
                        c.getThreshold()))
                .toList();
        return new OfferResponse(o.getId(),
                o.getTitle(),
                o.getDescription(),
                o.getDomain(), o.getLevel(),
                o.getMinExperienceYears(),
                o.getStartDate(),
                o.getClosingDate(),
                o.getStatus(),
                criteria,
                applicationCount);
    }

    static PublicOfferResponse toPublic(Offer o) {
        Organization org = o.getOrganization();
        return new PublicOfferResponse(o.getId(),
                o.getTitle(),
                o.getDescription(),
                o.getDomain(),
                o.getLevel(),
                o.getMinExperienceYears(),
                o.getStartDate(),
                o.getClosingDate(),
                o.getStatus(),
                org.getName(),
                org.getSlug());
    }
}
