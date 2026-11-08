package org.example.skulvi_cv.org;

/**
 * Cycle de vie d'une organisation, piloté par la facturation.
 *
 * <p>Une organisation qui ne paie plus n'est jamais supprimée et ne perd jamais l'accès : elle
 * redescend sur le plan gratuit. On ne détruit pas les données d'un client, d'une part parce
 * qu'il peut revenir, d'autre part parce que les CV qu'il a collectés engagent sa propre
 * responsabilité RGPD.
 */
public enum OrgStatus {

    /** Compte en service : plan gratuit ou abonnement à jour. */
    ACTIVE,

    /** Paiement en échec. Le service continue pendant la relance, les données restent intactes. */
    PAST_DUE,

    /** Abonnement résilié : retour aux limites du plan gratuit. */
    CANCELED;

    /**
     * Peut-on encore créer des offres et consommer des analyses ?
     *
     * <p>Toujours vrai : le plan gratuit reste utilisable quoi qu'il arrive. Ce sont les quotas
     * du plan qui limitent, pas le statut. Couper entièrement l'accès à un client qui a oublié
     * de mettre à jour sa carte fait perdre le client, pas gagner le paiement.
     */
    public boolean allowsWrite() {
        return true;
    }

    /** Faut-il signaler un problème de paiement dans l'interface ? */
    public boolean needsAttention() {
        return this == PAST_DUE;
    }
}
