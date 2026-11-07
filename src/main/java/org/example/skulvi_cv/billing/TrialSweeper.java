package org.example.skulvi_cv.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.org.OrgStatus;
import org.example.skulvi_cv.org.Organization;
import org.example.skulvi_cv.org.OrganizationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bascule les essais arrivés à échéance en lecture seule.
 *
 * <p>{@code Organization.trialExpired()} suffirait à bloquer les écritures sans ce balayage : le
 * contrôle est déjà fait à chaque requête. Mais tant que le statut en base reste {@code TRIAL},
 * la liste des clients côté plateforme ne montre pas qui est à relancer. Or un essai qui vient
 * d'expirer est précisément le moment où il faut décrocher son téléphone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialSweeper {

    private final OrganizationRepository organizations;

    /** Une fois par heure : la précision à la minute n'a aucun intérêt ici. */
    @Scheduled(fixedDelayString = "${talent.billing.trial-sweep-ms:3600000}",
            initialDelayString = "${talent.billing.trial-sweep-initial-ms:60000}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        List<Organization> expired = organizations.findAll().stream()
                .filter(o -> o.getStatus() == OrgStatus.TRIAL)
                .filter(o -> o.getTrialEndsAt() != null && o.getTrialEndsAt().isBefore(now))
                .toList();

        for (Organization org : expired) {
            org.setStatus(OrgStatus.SUSPENDED);
            organizations.save(org);
            log.info("Essai expiré pour « {} » ({}) : passage en lecture seule", org.getName(), org.getSlug());
        }
    }
}
