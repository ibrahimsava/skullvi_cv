import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { environment } from '../../../environments/environment';
import { AuthResponse, OrgRole, OrgSignupRequest, User, UserRequest } from './model';

const TOKEN_KEY = 'accessToken';
const USER_KEY = 'user';

interface TokenClaims {
  exp?: number;
  email?: string;
  roles?: string[] | string;
  org?: string;
  orgRole?: OrgRole;
}

/**
 * Session de l'utilisateur : jeton, identité, droits.
 *
 * <p>L'identité vient de `localStorage` et est exposée par un signal, pour que l'interface
 * réagisse à une connexion ou une déconnexion sans qu'on ait à propager l'information à la main.
 *
 * <p>Les droits sont lus dans le jeton et non dans l'objet utilisateur stocké : le jeton est
 * signé par le serveur, alors qu'une valeur recopiée dans `localStorage` peut être modifiée
 * depuis la console du navigateur. Cela ne protège rien côté serveur — l'API refait tous les
 * contrôles — mais évite d'afficher un écran auquel l'utilisateur n'aura de toute façon pas accès.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;

  private readonly currentUser = signal<User | null>(readStoredUser());

  /** Identité du compte connecté, ou null. */
  readonly user = this.currentUser.asReadonly();

  readonly isRecruiter = computed(() => this.currentUser()?.organizationId != null);

  readonly organizationName = computed(() => this.currentUser()?.organizationName ?? null);

  // ---------------------------------------------------------------- appels

  /** Inscription d'un candidat. */
  createUser(request: UserRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.api}/auth/register`, request)
      .pipe(tap((res) => this.store(res)));
  }

  /** Création d'un espace recruteur, en essai gratuit. */
  signupOrganization(request: OrgSignupRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.api}/auth/signup-organization`, request)
      .pipe(tap((res) => this.store(res)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.api}/auth/login`, { email, password })
      .pipe(tap((res) => this.store(res)));
  }

  /** Rafraîchit l'identité stockée depuis le serveur (plan modifié, organisation renommée…). */
  refreshUser(): Observable<User> {
    return this.http.get<User>(`${this.api}/me`)
      .pipe(tap((user) => {
        this.currentUser.set(user);
        safeSet(USER_KEY, JSON.stringify(user));
      }));
  }

  // ---------------------------------------------------------------- session

  store(response: AuthResponse): void {
    safeSet(TOKEN_KEY, response.accessToken);
    if (response.user) {
      safeSet(USER_KEY, JSON.stringify(response.user));
      this.currentUser.set(response.user);
    }
  }

  logout(): void {
    safeRemove(TOKEN_KEY);
    safeRemove(USER_KEY);
    this.currentUser.set(null);
  }

  token(): string | null {
    return safeGet(TOKEN_KEY);
  }

  /** Vrai si un jeton non expiré est présent. */
  isLoggedIn(): boolean {
    const claims = this.claims();
    if (!claims) return false;
    return !claims.exp || claims.exp * 1000 > Date.now();
  }

  // ---------------------------------------------------------------- droits

  /** Rôles portés par le jeton, sans le préfixe `ROLE_`. */
  roles(): string[] {
    const raw = this.claims()?.roles ?? [];
    const list = Array.isArray(raw) ? raw : [raw];
    return list.map((r) => String(r).replace('ROLE_', ''));
  }

  /** Rôle dans l'organisation courante, ou null pour un candidat. */
  orgRole(): OrgRole | null {
    return this.claims()?.orgRole ?? null;
  }

  /** L'utilisateur a-t-il accès à l'espace recruteur ? */
  hasOrganization(): boolean {
    return !!this.claims()?.org;
  }

  /** Peut-il créer et modifier (propriétaire ou recruteur, pas un simple lecteur) ? */
  canWrite(): boolean {
    const role = this.orgRole();
    return role === 'OWNER' || role === 'RECRUITER';
  }

  /** Gère-t-il l'abonnement et l'équipe ? */
  canManageOrganization(): boolean {
    return this.orgRole() === 'OWNER';
  }

  /** Administrateur de la plateforme Skulvi — pas un client. */
  isPlatformAdmin(): boolean {
    return this.roles().includes('ADMIN');
  }

  getUserEmail(): string | null {
    return this.currentUser()?.email ?? this.claims()?.email ?? null;
  }

  private claims(): TokenClaims | null {
    const token = safeGet(TOKEN_KEY);
    if (!token) return null;
    try {
      return jwtDecode<TokenClaims>(token);
    } catch {
      // Jeton illisible : on le traite comme absent plutôt que de laisser l'erreur remonter.
      return null;
    }
  }
}

/**
 * Accès à `localStorage` protégés.
 *
 * <p>La lecture échoue en navigation privée sur certains navigateurs, et l'écriture quand le
 * quota est atteint. Une exception non rattrapée empêcherait alors l'application de démarrer.
 */
function safeGet(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}

function safeSet(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* stockage indisponible */ }
}

function safeRemove(key: string): void {
  try { localStorage.removeItem(key); } catch { /* stockage indisponible */ }
}

function readStoredUser(): User | null {
  const raw = safeGet(USER_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as User; } catch { return null; }
}
