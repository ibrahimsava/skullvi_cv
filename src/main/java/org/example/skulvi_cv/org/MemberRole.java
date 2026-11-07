package org.example.skulvi_cv.org;

/** Rôle d'un utilisateur à l'intérieur d'une organisation. */
public enum MemberRole {

    /** Crée l'organisation, gère l'abonnement et les membres. */
    OWNER,

    /** Gère les offres et les candidatures, mais pas la facturation ni les membres. */
    RECRUITER,

    /** Consultation seule : utile pour un manager qui veut voir les classements. */
    VIEWER;

    public boolean canManageBilling() {
        return this == OWNER;
    }

    public boolean canManageMembers() {
        return this == OWNER;
    }

    public boolean canWrite() {
        return this == OWNER || this == RECRUITER;
    }
}
