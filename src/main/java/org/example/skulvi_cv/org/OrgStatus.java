package org.example.skulvi_cv.org;

/**
 * Cycle de vie d'une organisation, piloté par la facturation.
 *
 * <p>Une organisation qui ne paie plus n'est jamais supprimée : elle passe en lecture seule
 * ({@link #PAST_DUE} puis {@link #SUSPENDED}). On ne détruit pas les données d'un client,
 * d'une part parce qu'il peut revenir, d'autre part parce que les CV qu'il a collectés
 * engagent sa propre responsabilité RGPD.
 */
public enum OrgStatus {

    /** Période d'essai en cours. */
    TRIAL,

    /** Abonnement à jour. */
    ACTIVE,

    /** Paiement en échec : lecture seule, les données restent intactes. */
    PAST_DUE,

    /** Essai expiré ou abonnement résilié : lecture seule. */
    SUSPENDED;

    /** Peut-on encore créer des offres et consommer des analyses ? */
    public boolean allowsWrite() {
        return this == TRIAL || this == ACTIVE;
    }
}
