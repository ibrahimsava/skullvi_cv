import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { AuthResponse } from '../model';
import { Auth } from './auth';

function route(path: string, suite?: string) {
  return {
    snapshot: {
      routeConfig: { path },
      queryParamMap: { get: (key: string) => (key === 'suite' ? (suite ?? null) : null) },
    },
  };
}

function tokenResponse(organizationId: string | null): AuthResponse {
  return {
    accessToken: 'jeton-de-test',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    user: {
      id: 'u1',
      email: 'test@exemple.fr',
      firstName: 'Test',
      lastName: 'Utilisateur',
      role: organizationId ? 'RECRUTEUR' : 'CANDIDAT',
      organizationId,
      organizationName: organizationId ? 'Cabinet Leon' : null,
      orgRole: organizationId ? 'OWNER' : null,
      plan: organizationId ? 'FREE' : null,
    },
  };
}

function setup(path = 'login', suite?: string) {
  TestBed.configureTestingModule({
    imports: [Auth],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: route(path, suite) },
    ],
  });

  const http = TestBed.inject(HttpTestingController);
  const router = TestBed.inject(Router);
  const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

  const fixture = TestBed.createComponent(Auth);
  fixture.detectChanges();

  return { fixture, http, navigate, component: fixture.componentInstance };
}

describe('Auth', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('se construit en mode connexion sur /login', () => {
    const { component } = setup('login');
    expect(component.isLogin).toBe(true);
  });

  it('se construit en mode inscription sur /register', () => {
    const { component } = setup('register');
    expect(component.isLogin).toBe(false);
  });

  /*
   * Le cœur de la redirection : la destination vient de `organizationId`, pas d'un rôle.
   * L'ancienne version testait un rôle « ADMIN » qui n'appartient plus qu'à l'administrateur
   * de la plateforme, et aurait envoyé tous les recruteurs sur la liste des offres publiques.
   */
  it('envoie un recruteur vers son espace de travail', () => {
    const { component, http, navigate } = setup('login');
    component.userRequest = { firstName: '', lastName: '', email: 'a@b.fr', password: 'motdepasse1' };
    component.submit();

    http.expectOne((r) => r.url.endsWith('/auth/login')).flush(tokenResponse('org-1'));

    expect(navigate).toHaveBeenCalledWith('/admin/dashboard');
  });

  it('envoie un candidat vers son espace personnel', () => {
    const { component, http, navigate } = setup('login');
    component.userRequest = { firstName: '', lastName: '', email: 'a@b.fr', password: 'motdepasse1' };
    component.submit();

    http.expectOne((r) => r.url.endsWith('/auth/login')).flush(tokenResponse(null));

    expect(navigate).toHaveBeenCalledWith('/mon-espace');
  });

  it('revient sur la page demandée avant la connexion', () => {
    const { component, http, navigate } = setup('login', '/admin/abonnement');
    component.userRequest = { firstName: '', lastName: '', email: 'a@b.fr', password: 'motdepasse1' };
    component.submit();

    http.expectOne((r) => r.url.endsWith('/auth/login')).flush(tokenResponse('org-1'));

    expect(navigate).toHaveBeenCalledWith('/admin/abonnement');
  });

  it('affiche un message clair sur mot de passe incorrect', () => {
    const { component, http } = setup('login');
    component.userRequest = { firstName: '', lastName: '', email: 'a@b.fr', password: 'faux' };
    component.submit();

    http.expectOne((r) => r.url.endsWith('/auth/login'))
      .flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(component.isError()).toBe(true);
    expect(component.message()).toBe('E-mail ou mot de passe incorrect.');
    expect(component.loading()).toBe(false);
  });

  it('signale un e-mail déjà pris à l\'inscription', () => {
    const { component, http } = setup('register');
    component.userRequest = { firstName: 'A', lastName: 'B', email: 'a@b.fr', password: 'motdepasse1' };
    component.submit();

    http.expectOne((r) => r.url.endsWith('/auth/register'))
      .flush('conflit', { status: 409, statusText: 'Conflict' });

    expect(component.message()).toBe('Cette adresse e-mail est déjà utilisée.');
  });
});
