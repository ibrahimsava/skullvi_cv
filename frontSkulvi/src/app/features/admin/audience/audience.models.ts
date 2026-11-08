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
