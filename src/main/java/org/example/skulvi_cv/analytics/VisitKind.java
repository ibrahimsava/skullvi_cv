package org.example.skulvi_cv.analytics;

/** Nature de l'événement mesuré. */
public enum VisitKind {

    /** Affichage d'une page quelconque. */
    PAGE,

    /** Consultation d'une offre : première étape de l'entonnoir. */
    OFFER_VIEW,

    /** Ouverture du formulaire de candidature. */
    APPLY_START,

    /** Candidature effectivement envoyée : fin de l'entonnoir. */
    APPLY_DONE,

    /** Création d'un espace recruteur. */
    SIGNUP_ORG,

    /** Création d'un compte candidat. */
    SIGNUP_CANDIDATE
}
