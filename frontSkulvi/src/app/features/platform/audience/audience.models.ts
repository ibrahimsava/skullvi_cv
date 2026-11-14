/** Un point de la série journalière. */
export interface DayPoint {
  day: string;
  visits: number;
  visitors: number;
}

/** Une part d'une répartition. Le pourcentage est calculé par le serveur. */
export interface Slice {
  label: string;
  count: number;
  share: number;
}

/** Entonnoir : de la consultation d'une offre à la candidature envoyée. */
export interface Funnel {
  offerViews: number;
  applyStarted: number;
  applySent: number;
  startRate: number;
  completionRate: number;
  overallRate: number;
}

/**
 * Un indicateur et sa série journalière : de quoi dessiner un « petit multiple ».
 *
 * <p>`daily` est aligné sur `series` — un point par jour de la période, zéros compris. Chaque
 * vignette a sa propre échelle verticale : on compare des formes, pas des hauteurs.
 */
export interface Metric {
  key: string;
  label: string;
  total: number;
  /** Variation en pourcentage. Null quand la période précédente est vide. */
  trend: number | null;
  daily: number[];
}

/** Tout ce qu'affiche la page de statistiques, en un seul appel. */
export interface AnalyticsOverview {
  days: number;
  from: string;
  to: string;
  visits: number;
  visitors: number;
  visitsPrevious: number;
  visitorsPrevious: number;
  /** Variation en pourcentage. Null quand la période précédente est vide. */
  visitsTrend: number | null;
  visitorsTrend: number | null;
  series: DayPoint[];
  /** Un indicateur par vignette, tous alignés sur le même axe de temps. */
  metrics: Metric[];
  topPages: Slice[];
  referrers: Slice[];
  devices: Slice[];
  funnel: Funnel;
  candidateSignups: number;
  organizationSignups: number;
}

/** Totaux de la plateforme, réservés à l'administrateur Skulvi. */
export interface PlatformTotals {
  organizations: number;
  organizationsLast30: number;
  users: number;
}
