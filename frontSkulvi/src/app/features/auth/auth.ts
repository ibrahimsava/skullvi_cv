import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { UserRequest, AuthResponse } from './model';
import { Observable } from 'rxjs';

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

}
