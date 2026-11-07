import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PlanCatalog, PublicOffer } from '../admin/admin.models';

/** En-tête d'une page carrière d'entreprise. */
export interface PublicOrganization {
  name: string;
  slug: string;
  openOffers: number;
}

/**
 * Offres consultables sans compte.
 *
 * <p>Pointe sur `/public/*` et non plus sur `/offers` : cette dernière route ne renvoie
 * désormais que les offres de l'organisation du jeton. Appelée sans compte, elle répondrait 401,
 * et appelée par un recruteur, elle masquerait les offres des autres entreprises — alors que la
 * page publique doit justement toutes les montrer.
 */
@Injectable({ providedIn: 'root' })
export class OffresService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiBaseUrl}/public`;

  /** Toutes les offres ouvertes, toutes entreprises confondues. */
  list(): Observable<PublicOffer[]> {
    return this.http.get<PublicOffer[]>(`${this.api}/offers`);
  }

  get(id: string): Observable<PublicOffer> {
    return this.http.get<PublicOffer>(`${this.api}/offers/${id}`);
  }

  /** Les offres d'une seule entreprise : sa page carrière. */
  byOrganization(slug: string): Observable<PublicOffer[]> {
    return this.http.get<PublicOffer[]>(`${this.api}/orgs/${slug}/offers`);
  }

  organization(slug: string): Observable<PublicOrganization> {
    return this.http.get<PublicOrganization>(`${this.api}/orgs/${slug}`);
  }

  /** Grille tarifaire publique, pour la page de tarifs. */
  plans(): Observable<PlanCatalog> {
    return this.http.get<PlanCatalog>(`${this.api}/plans`);
  }
}
