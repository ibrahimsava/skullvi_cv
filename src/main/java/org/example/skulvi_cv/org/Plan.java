package org.example.skulvi_cv.org;

/**
 * Les plans d'abonnement et leurs limites.
 *
 * <p>Les limites sont dans le code (et non en base) volontairement : une limite est une règle
 * métier, elle doit être versionnée avec l'application et testable. Seul le plan choisi par
 * l'organisation est stocké.
 *
 * <p>L'unité facturée est l'<b>analyse de CV</b> : c'est à la fois le coût réel (temps CPU du
 * modèle) et la valeur perçue par le recruteur. Les deux sont alignés, la marge est prévisible.
 */
public enum Plan {

    /**
     * Gratuit, sans limite de durée.
     *
     * <p>Remplace l'ancien essai de 14 jours. Un essai qui expire oblige à décider avant
     * d'avoir eu un vrai besoin ; un palier gratuit permanent laisse l'entreprise s'installer
     * et ne paie que le jour où elle recrute pour de bon. C'est le quota mensuel d'analyses
     * qui déclenche la conversion, pas un compte à rebours.
     */
    FREE("Gratuit", 0, 2, 40, 2, 0),

    STARTER("Starter", 900, 5, 250, 4, 1),

    PRO("Pro", 1_400, 15, 800, 10, 2),

    /**
     * Tout illimité, à 20 € par mois.
     *
     * <p>Plafond de prix décidé : au-delà, l'offre sort de ce que paie une PME sans service
     * RH. La contrepartie à surveiller est la marge — une analyse coûte du temps CPU, et ce
     * plan n'en plafonne plus le nombre. Si un client venait à consommer des milliers
     * d'analyses par mois, c'est la priorité de file qui protège les autres, pas le prix.
     */
    BUSINESS("Illimité", 2_000, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 3);

    /**
     * Valeur sentinelle pour « pas de limite » : évite un Integer null à tester partout.
     * Les constantes ci-dessus utilisent {@code Integer.MAX_VALUE} directement, car un champ
     * statique de l'enum n'est pas encore initialisé au moment où les constantes sont créées.
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * Prix facturé par analyse au-delà du quota mensuel, en centimes.
     *
     * <p>Ne concerne que Starter et Pro : le plan illimité n'a pas de quota à dépasser.
     */
    public static final int OVERAGE_CENTS = 10;

    private final String label;
    private final int priceCents;
    private final int maxActiveOffers;
    private final int maxAnalysesPerMonth;
    private final int maxSeats;
    /** Plus la valeur est haute, plus les analyses de l'organisation passent devant dans la file. */
    private final int queuePriority;

    Plan(String label, int priceCents, int maxActiveOffers, int maxAnalysesPerMonth, int maxSeats, int queuePriority) {
        this.label = label;
        this.priceCents = priceCents;
        this.maxActiveOffers = maxActiveOffers;
        this.maxAnalysesPerMonth = maxAnalysesPerMonth;
        this.maxSeats = maxSeats;
        this.queuePriority = queuePriority;
    }

    public String label() { return label; }

    public int priceCents() { return priceCents; }

    public int maxActiveOffers() { return maxActiveOffers; }

    public int maxAnalysesPerMonth() { return maxAnalysesPerMonth; }

    public int maxSeats() { return maxSeats; }

    public int queuePriority() { return queuePriority; }

    /**
     * Le dépassement est-il facturé à l'analyse ?
     *
     * <p>Faux pour le gratuit, qui s'arrête à son quota, et faux pour l'illimité, qui n'en
     * a pas. Seuls les paliers intermédiaires facturent le surplus.
     */
    public boolean allowsOverage() {
        return this != FREE && maxAnalysesPerMonth != UNLIMITED;
    }

    /** Ce plan lève-t-il toute limite ? */
    public boolean isUnlimited() {
        return maxAnalysesPerMonth == UNLIMITED;
    }

    public boolean isPaid() {
        return priceCents > 0;
    }

    /** Clé de configuration du tarif chez le prestataire de paiement. */
    public String priceKey() {
        return name().toLowerCase();
    }
}
