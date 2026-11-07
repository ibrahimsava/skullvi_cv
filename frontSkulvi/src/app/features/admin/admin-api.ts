import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrgRole } from '../auth/model';
import {
  ApplicationSummary, DashboardStats, InviteRequest, MemberList, Offer,
  Organization, PlanCatalog, RankedCandidate, ScoreResponse, UsageSnapshot,
} from './admin.models';

/** Appels de l'espace recruteur. Toutes ces routes sont filtrées côté serveur par l'organisation du jeton. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;   // se termine par /api/v1

  // ---------------------------------------------------------------- recrutement

  getDashboard(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.api}/dashboard`);
  }
  getOffers(): Observable<Offer[]> {
    return this.http.get<Offer[]>(`${this.api}/offers`);
  }
  createOffer(payload: unknown): Observable<Offer> {
    return this.http.post<Offer>(`${this.api}/offers`, payload);
  }
  getRanking(offerId: string): Observable<RankedCandidate[]> {
    return this.http.get<RankedCandidate[]>(`${this.api}/offers/${offerId}/ranking`);
  }
  getApplications(offerId: string): Observable<ApplicationSummary[]> {
    return this.http.get<ApplicationSummary[]>(`${this.api}/offers/${offerId}/applications`);
  }
  getScore(applicationId: string): Observable<ScoreResponse> {
    return this.http.get<ScoreResponse>(`${this.api}/applications/${applicationId}/score`);
  }
  analyze(applicationId: string) {
    return this.http.post(`${this.api}/applications/${applicationId}/analyze`, {});
  }
  closeOffer(offerId: string): Observable<Offer> {
    return this.http.post<Offer>(`${this.api}/offers/${offerId}/close`, {});
  }
  reopenOffer(offerId: string): Observable<Offer> {
    return this.http.post<Offer>(`${this.api}/offers/${offerId}/reopen`, {});
  }
  downloadCv(applicationId: string) {
    return this.http.get(`${this.api}/applications/${applicationId}/cv`, { responseType: 'blob' });
  }
  deleteApplication(applicationId: string) {
    return this.http.delete<void>(`${this.api}/applications/${applicationId}`);
  }

  // ---------------------------------------------------------------- organisation

  getOrganization(): Observable<Organization> {
    return this.http.get<Organization>(`${this.api}/org`);
  }
  renameOrganization(name: string): Observable<Organization> {
    return this.http.put<Organization>(`${this.api}/org`, { name });
  }
  getUsage(): Observable<UsageSnapshot> {
    return this.http.get<UsageSnapshot>(`${this.api}/org/usage`);
  }
  getPlans(): Observable<PlanCatalog> {
    return this.http.get<PlanCatalog>(`${this.api}/org/plans`);
  }

  // ---------------------------------------------------------------- équipe

  getMembers(): Observable<MemberList> {
    return this.http.get<MemberList>(`${this.api}/org/members`);
  }
  invite(request: InviteRequest) {
    return this.http.post(`${this.api}/org/members`, request);
  }
  changeRole(membershipId: string, role: OrgRole) {
    return this.http.put(`${this.api}/org/members/${membershipId}/role`, { role });
  }
  removeMember(membershipId: string) {
    return this.http.delete<void>(`${this.api}/org/members/${membershipId}`);
  }
}
