package org.example.skulvi_cv.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.common.ApiException;
import org.example.skulvi_cv.org.OrgStatus;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.example.skulvi_cv.org.Plan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Applique l'état de l'abonnement sur l'organisation.
 *
 * <p>Séparé de {@link StripeService} : celui-ci parle au prestataire, celui-là décide ce que
 * ça change en base. Le jour où le prestataire change — et la question se pose sérieusement
 * selon les pays — seule la passerelle est à réécrire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final OrganizationRepository organizations;

    /** Démarre ou met à jour un abonnement payant. */
    @Transactional
    public void activate(UUID orgId, Plan plan, String subscriptionId,
                         Instant periodEnd, boolean cancelAtPeriodEnd) {
        Organization org = require(orgId);

        org.setPlan(plan);
        org.setStatus(OrgStatus.ACTIVE);
        org.setBillingSubscriptionId(subscriptionId);
        org.setCurrentPeriodEnd(periodEnd);
        org.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        organizations.save(org);

        log.info("Organisation {} : abonnement {} actif sur le plan {}", orgId, subscriptionId, plan);
    }

    /** Signale un paiement en échec. Le service continue pendant la relance. */
    @Transactional
    public void markPastDue(UUID orgId) {
        Organization org = require(orgId);
        org.setStatus(OrgStatus.PAST_DUE);
        organizations.save(org);
        log.warn("Organisation {} : paiement en échec", orgId);
    }

    /**
     * Fin de l'abonnement : retour au plan gratuit.
     *
     * <p>Aucune donnée n'est supprimée et l'accès reste ouvert — simplement avec les quotas du
     * gratuit. Un client qui revient retrouve ses offres et ses candidatures intactes.
     */
    @Transactional
    public void revertToFree(UUID orgId) {
        Organization org = require(orgId);

        org.setPlan(Plan.FREE);
        org.setStatus(OrgStatus.ACTIVE);
        org.setBillingSubscriptionId(null);
        org.setCurrentPeriodEnd(null);
        org.setCancelAtPeriodEnd(false);
        organizations.save(org);

        log.info("Organisation {} : retour au plan gratuit", orgId);
    }

    /** Mémorise l'identifiant client créé lors du premier passage en caisse. */
    @Transactional
    public void rememberCustomer(UUID orgId, String customerId) {
        Organization org = require(orgId);
        if (customerId != null && !customerId.equals(org.getBillingCustomerId())) {
            org.setBillingCustomerId(customerId);
            organizations.save(org);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Organization> findByCustomerId(String customerId) {
        return organizations.findByBillingCustomerId(customerId);
    }

    private Organization require(UUID orgId) {
        return organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Organisation introuvable : " + orgId));
    }
}
