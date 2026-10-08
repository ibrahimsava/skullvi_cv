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
    isLoggedIn(): boolean {
        return !!localStorage.getItem('accessToken');
    }

    // Extrait le rôle du token JWT
    getUserRole(): string | null {
        const token = localStorage.getItem('accessToken');
        if (!token) return null;

        try {
            const decoded: any = jwtDecode(token);
            // Recuperation du 'role'
            return decoded.role  || null; 
        } catch (error) {
            return null;
        }
    }

    // Vérifie si l'utilisateur possède le rôle requis
    isAdmin(): boolean {
        return this.getUserRole() === 'ADMIN';
    }

}
