export enum CriterionType {
  SKILL = 'SKILL',
  EXPERIENCE = 'EXPERIENCE',
  EDUCATION = 'EDUCATION',
  PROJECT = 'PROJECT',
}

export interface Criterion {
  id: string;
  name: string;
  type: CriterionType;
  mandatory: boolean;
  weight: number;
  threshold: number;
}

export enum OfferStatus {
  DRAFT = 'DRAFT',
  OPEN = 'OPEN',
  CLOSED = 'CLOSED',
}

export interface Offre {
  id: string;
  title: string;
  description: string;
  domain: string;
  level: string;
  minExperienceYears: number;
  startDate: string; // ISO date string (e.g. '2026-10-07')
  closingDate: string; // ISO date string
  status: OfferStatus;
  criteria: Criterion[];
}
