import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AnalyticsOverview, PlatformTotals } from './audience.models';

@Injectable({ providedIn: 'root' })
export class AudienceApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;

  /** Fréquentation des pages de l'organisation connectée. */
  overview(days: number): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.api}/analytics/overview`, { params: { days } });
  }

  /** Fréquentation de tout le site. Réservé à l'administrateur de la plateforme. */
  platformOverview(days: number): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.api}/platform/analytics`, { params: { days } });
  }

  platformTotals(): Observable<PlatformTotals> {
    return this.http.get<PlatformTotals>(`${this.api}/platform/totals`);
  }
}
