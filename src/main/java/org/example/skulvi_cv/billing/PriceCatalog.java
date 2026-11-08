package org.example.skulvi_cv.billing;

import lombok.extern.slf4j.Slf4j;
import org.example.skulvi_cv.org.Plan;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Correspondance entre les tarifs du prestataire et nos plans, dans les deux sens.
 *
 * <p>La configuration donne plan → identifiant de tarif. Le webhook a besoin de l'inverse :
 * il reçoit un identifiant de tarif et doit savoir quel plan accorder. On construit donc la
 * table retournée une fois au démarrage, plutôt que de parcourir la configuration à chaque
 * événement.
 */
@Slf4j
@Component
public class PriceCatalog {

    private final Map<String, Plan> byPriceId = new HashMap<>();

    public PriceCatalog(StripeProperties props) {
        for (Plan plan : Plan.values()) {
            String priceId = props.priceFor(plan.priceKey());
            if (priceId == null || priceId.isBlank()) {
                continue;
            }
            Plan previous = byPriceId.put(priceId, plan);
            if (previous != null) {
                // Deux plans sur le même tarif : la configuration est fausse, et le webhook
                // accorderait un plan au hasard. Mieux vaut le dire fort au démarrage.
                log.error("Le tarif {} est configuré à la fois pour {} et {} : corrigez la "
                        + "configuration, un client pourrait recevoir le mauvais plan.",
                        priceId, previous, plan);
            }
        }
        log.info("Catalogue de tarifs : {} plan(s) souscriptible(s) en ligne", byPriceId.size());
    }

    /** @return le plan correspondant au tarif, ou {@code null} si le tarif est inconnu */
    public Plan planFor(String priceId) {
        return priceId == null ? null : byPriceId.get(priceId);
    }
}
