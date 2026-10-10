export interface DashboardStats {
  offers: number;
  applications: number;
  scored: number;
  failed: number;
  highPriority: number;
  averageScore: number;
}

export interface Criterion {
  id: string;
  name: string;
  type: string;
  mandatory: boolean;
  weight: number;
  threshold: number;
}

export interface Offer {
  id: string;
  title: string;
  description: string;
  domain: string;
  level: string;
  minExperienceYears: number;
  startDate: string;
  closingDate: string;
  status: string;
  criteria: Criterion[];
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