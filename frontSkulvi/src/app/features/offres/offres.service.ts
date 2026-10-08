import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Offre } from './model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OffresService {

  Offre: Offre[]=[] 

  private readonly apiUrl = `${environment.apiBaseUrl}/offers`;

  private readonly http = inject(HttpClient);

  list(): Observable<Offre[]> {
    return this.http.get<Offre[]>(this.apiUrl);
  }

  get(id: number): Observable<Offre> {
    return this.http.get<Offre>(`${this.apiUrl}/${id}`);
  }

  create(offre: Offre): Observable<Offre> {
    return this.http.post<Offre>(this.apiUrl, offre);
  }


  // placeholder for create/update/delete if needed
}
