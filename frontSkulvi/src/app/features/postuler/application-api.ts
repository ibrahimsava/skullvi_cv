import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Offer } from '../admin/admin.models';

export interface MeResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class ApplicationApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;   // se termine par /api/v1

  getOffer(id: string) {
    return this.http.get<Offer>(`${this.api}/offers/${id}`);
  }

  getMe() {
    return this.http.get<MeResponse>(`${this.api}/me`);
  }

  // multipart : ne pas définir Content-Type, le navigateur ajoute la bonne frontière
  apply(offerId: string, body: FormData) {
    return this.http.post(`${this.api}/offers/${offerId}/applications`, body);
  }
}