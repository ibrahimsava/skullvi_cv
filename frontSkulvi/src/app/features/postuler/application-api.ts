import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PublicOffer } from '../admin/admin.models';
import { User } from '../auth/model';

@Injectable({ providedIn: 'root' })
export class ApplicationApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;   // se termine par /api/v1

  /**
   * Détail public de l'offre.
   *
   * <p>Passe par `/public/offers/{id}` : la route `/offers/{id}` est désormais réservée au
   * propriétaire de l'offre et renverrait 401 à un visiteur, 404 à un recruteur d'une autre
   * entreprise.
   */
  getOffer(id: string): Observable<PublicOffer> {
    return this.http.get<PublicOffer>(`${this.api}/public/offers/${id}`);
  }

  getMe(): Observable<User> {
    return this.http.get<User>(`${this.api}/me`);
  }

  /** Multipart : ne pas définir Content-Type, le navigateur ajoute la bonne frontière. */
  apply(offerId: string, body: FormData) {
    return this.http.post(`${this.api}/offers/${offerId}/applications`, body);
  }
}
