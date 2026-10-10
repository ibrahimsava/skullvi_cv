import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { UserRequest, AuthResponse } from './model';
import { Observable } from 'rxjs';
import { jwtDecode } from 'jwt-decode';

@Injectable({ providedIn: 'root' })
export class AuthService {

    apiUrl = environment.apiBaseUrl + '/auth';

    private readonly http = inject(HttpClient);

    createUser(userRequest: UserRequest): Observable<UserRequest> {
        return this.http.post<UserRequest>(`${this.apiUrl}/register`, userRequest);
    }

    login(email: string, password: string): Observable<AuthResponse> {
        return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { email, password });
    }

    // Détermine si un token est présent
   // isLoggedIn(): boolean {
      //  return !!localStorage.getItem('accessToken');
         //}

    isLoggedIn(): boolean {
  const token = localStorage.getItem('accessToken');
  if (!token) return false;
  try {
    const { exp } = jwtDecode<{ exp?: number }>(token);
    return !exp || exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

    // Retourne la liste des rôles du token
getUserRoles(): string[] {
  const token = localStorage.getItem('accessToken');
  if (!token) return [];

  try {
    const decoded: any = jwtDecode(token);
    const raw = decoded.roles ?? decoded.role ?? [];
    return Array.isArray(raw) ? raw : [raw];
         } catch (error) {
            console.error('Erreur lors du décodage du token JWT :', error);
            return [];
        }
                return [];
                    }
    // Vérifie si l'utilisateur possède le rôle requis
            isAdmin(): boolean {
      return this.getUserRoles().
      some(r => r.replace('ROLE_', '') === 'ADMIN');
          }


          getUserEmail(): string | null {
  const token = localStorage.getItem('accessToken');
  if (!token) return null;
  try {
    return (jwtDecode(token) as any).email ?? null;
  } catch {
    return null;
  }
}
   

}
