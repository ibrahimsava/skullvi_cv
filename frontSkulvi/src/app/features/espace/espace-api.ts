import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User } from '../auth/model';

/** Statut d'une candidature, tel que montré au candidat. */
export type CandidateStatus =
  | 'SUBMITTED' | 'CV_UPLOADED' | 'ANALYZING' | 'ANALYZED'
  | 'ANALYSIS_FAILED' | 'SCORED' | 'SHORTLISTED' | 'REJECTED';

/**
 * Une candidature vue par son auteur.
 *
 * <p>Il n'y a volontairement pas de score : `statusLabel` est la phrase que le serveur destine
 * au candidat. Montrer une note sur 100 calculée par une machine, hors contexte et avant toute
 * décision humaine, serait à la fois blessant et trompeur.
 */
export interface MyApplication {
  id: string;
  offerId: string;
  offerTitle: string;
  organizationName: string;
  status: CandidateStatus;
  statusLabel: string;
  submittedAt: string;
}

export interface ProfileSkill { name: string; evidence: string | null; }
export interface ProfileEducation { degree: string | null; field: string | null; institution: string | null; }
export interface ProfileExperience {
  role: string | null;
  company: string | null;
  years: number | null;
  technologies: string[] | null;
}

export interface StructuredProfile {
  skills: ProfileSkill[];
  education: ProfileEducation[];
  experience: ProfileExperience[];
  projects: string[];
  languages: string[];
  certifications: string[];
  totalExperienceYears: number | null;
}

/** Ce que l'analyse a lu dans le CV. `available` est faux tant qu'aucune analyse n'a abouti. */
export interface MyProfile {
  available: boolean;
  applicationId: string | null;
  analyzedAt: string | null;
  profile: StructuredProfile | null;
}

export interface ContactUpdate {
  phone: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
}

/** Appels de l'espace personnel. Le serveur filtre tout sur le sujet du jeton. */
@Injectable({ providedIn: 'root' })
export class EspaceApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;

  me(): Observable<User> {
    return this.http.get<User>(`${this.api}/me`);
  }

  updateName(firstName: string, lastName: string): Observable<User> {
    return this.http.put<User>(`${this.api}/me`, { firstName, lastName });
  }

  myApplications(): Observable<MyApplication[]> {
    return this.http.get<MyApplication[]>(`${this.api}/me/applications`);
  }

  myProfile(): Observable<MyProfile> {
    return this.http.get<MyProfile>(`${this.api}/me/profile`);
  }

  updateContact(applicationId: string, update: ContactUpdate): Observable<MyApplication> {
    return this.http.put<MyApplication>(`${this.api}/me/applications/${applicationId}/contact`, update);
  }

  withdraw(applicationId: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/me/applications/${applicationId}`);
  }
}
