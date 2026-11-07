import { OrgRole, OrgStatus, PlanCode } from '../auth/model';

export interface DashboardStats {
  offers: number;
  openOffers: number;
  applications: number;
  scored: number;
  failed: number;
  highPriority: number;
  averageScore: number | null;
  analysesPending: number;
  analysesUsed: number;
  /** Null si le plan ne plafonne pas les analyses. */
  analysesLimit: number | null;
}

export interface Criterion {
  id: string;
  name: string;
  type: string;
  mandatory: boolean;
  weight: number;
  threshold: number | null;
}

/** Vue recruteur d'une offre : contient la grille de notation. */
export interface Offer {
  id: string;
  title: string;
  description: string;
  domain: string;
  level: string;
  minExperienceYears: number;
  startDate: string | null;
  closingDate: string | null;
  status: string;
  criteria: Criterion[];
  applications: number;
}

/**
 * Vue publique d'une offre.
 *
 * <p>Sans les critères : le serveur ne les envoie plus aux visiteurs, pour ne pas publier la
 * grille de notation que les candidats n'auraient plus qu'à recopier dans leur CV.
 */
export interface PublicOffer {
  id: string;
  title: string;
  description: string;
  domain: string;
  level: string;
  minExperienceYears: number;
  startDate: string | null;
  closingDate: string | null;
  status: string;
  organizationName: string;
  organizationSlug: string;
}

export interface ApplicationSummary {
  id: string;
  candidateName: string;
  email: string;
  status: string;
  score: number | null;
  priority: string | null;
  failureReason: string | null;
  submittedAt: string;
}

export interface RankedCandidate {
  rank: number;
  applicationId: string;
  candidateName: string;
  email: string;
  score: number;
  priority: string;
}

export interface MatchItem {
  criterion: string;
  type: string;
  mandatory: boolean;
  weight: number;
  ratio: number;
  matched: boolean;
  points: number;
  evidence: string;
}

export interface ScoreResponse {
  applicationId: string;
  candidateName: string;
  total: number;
  rawTotal: number;
  capped: boolean;
  priority: string;
  details: MatchItem[];
}

// ---------------------------------------------------------------- organisation

export interface Organization {
  id: string;
  name: string;
  slug: string;
  plan: PlanCode;
  planLabel: string;
  status: OrgStatus;
  trialEndsAt: string | null;
  writable: boolean;
  createdAt: string;
}

/** Consommation du mois face aux limites du plan. Les limites nulles valent « illimité ». */
export interface UsageSnapshot {
  plan: PlanCode;
  planLabel: string;
  status: OrgStatus;
  trialEndsAt: string | null;
  trialDaysLeft: number | null;
  period: string;
  analysesUsed: number;
  analysesLimit: number | null;
  overageCount: number;
  overageCents: number;
  activeOffers: number;
  activeOffersLimit: number | null;
  seats: number;
  seatsLimit: number | null;
  writable: boolean;
}

export interface PlanOption {
  plan: PlanCode;
  label: string;
  priceCents: number;
  maxActiveOffers: number | null;
  maxAnalysesPerMonth: number | null;
  maxSeats: number | null;
  allowsOverage: boolean;
  overageCents: number;
  current: boolean;
}

export interface PlanCatalog {
  plans: PlanOption[];
}

export interface Member {
  membershipId: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: OrgRole;
  me: boolean;
  joinedAt: string;
}

export interface MemberList {
  members: Member[];
  seats: number;
  seatsLimit: number | null;
}

export interface InviteRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role: OrgRole;
}
