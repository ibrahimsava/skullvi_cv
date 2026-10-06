package org.example.skulvi_cv.offer;

import lombok.RequiredArgsConstructor;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.offer.OfferDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository repository;

    @Transactional
    public OfferResponse create(OfferRequest r) {
        Offer o = new Offer();
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
        return toResponse(repository.save(o));
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> list() {
        return repository.findAll().stream().map(OfferService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OfferResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public OfferResponse close(UUID id) {
        Offer o = getEntity(id);
        o.setStatus(OfferStatus.CLOSED);
        return toResponse(repository.save(o));
    }

    public Offer getEntity(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Offre introuvable : " + id));
    }

    static OfferResponse toResponse(Offer o) {
        List<CriterionResponse> criteria = o.getCriteria().stream()
                .map(c -> new CriterionResponse(c.getId(), c.getName(), c.getType(), c.isMandatory(), c.getWeight(), c.getThreshold()))
                .toList();
        return new OfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getDomain(), o.getLevel(),
                o.getMinExperienceYears(), o.getStartDate(), o.getClosingDate(), o.getStatus(), criteria);
    }
}
