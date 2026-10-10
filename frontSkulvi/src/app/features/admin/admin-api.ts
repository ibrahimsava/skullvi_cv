import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApplicationSummary, DashboardStats, Offer, RankedCandidate, ScoreResponse,
} from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;   // doit se terminer par /api/v1

  getDashboard(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.api}/dashboard`);
  }
  getOffers(): Observable<Offer[]> {
    return this.http.get<Offer[]>(`${this.api}/offers`);
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
  closeOffer(offerId: string) {
    return this.http.post(`${this.api}/offers/${offerId}/close`, {});
  }
  downloadCv(applicationId: string) {
    return this.http.get(`${this.api}/applications/${applicationId}/cv`, { responseType: 'blob' });
  }
  
  deleteApplication(applicationId: string) {
  return this.http.delete<void>(`${this.api}/applications/${applicationId}`);
}
}