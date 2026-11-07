import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { AuthService } from '../../features/auth/auth';
import { recruiterGuard } from './recruiter-guard';

/** Jeton signé fictif : seule la charge utile est lue par le service de session. */
function fakeToken(payload: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(payload)}.signature`;
}

const inOneHour = Math.floor(Date.now() / 1000) + 3600;

function runGuard() {
  return TestBed.runInInjectionContext(() =>
    recruiterGuard({} as ActivatedRouteSnapshot, { url: '/admin/dashboard' } as RouterStateSnapshot));
}

describe('recruiterGuard', () => {
  let navigate: ReturnType<typeof vi.spyOn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), AuthService] });

    const router = TestBed.inject(Router);
    navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  it('laisse passer un membre d\'une organisation', () => {
    localStorage.setItem('accessToken', fakeToken({
      exp: inOneHour, org: 'org-1', orgRole: 'OWNER', roles: ['RECRUTEUR', 'OWNER'],
    }));

    expect(runGuard()).toBe(true);
  });

  it('renvoie un visiteur non connecté vers la connexion', () => {
    expect(runGuard()).toBe(false);
    expect(navigate).toHaveBeenCalled();
  });

  /*
   * Un candidat connecté n'est pas renvoyé au login : il est bien authentifié, il s'est
   * seulement trompé d'adresse. Le renvoyer vers la connexion l'enfermerait dans une boucle.
   */
  it('renvoie un candidat connecté vers son espace personnel', () => {
    localStorage.setItem('accessToken', fakeToken({
      exp: inOneHour, roles: ['CANDIDAT'],
    }));

    expect(runGuard()).toBe(false);
    expect(navigateByUrl).toHaveBeenCalledWith('/mon-espace', { replaceUrl: true });
  });

  it('refuse un jeton expiré', () => {
    localStorage.setItem('accessToken', fakeToken({
      exp: Math.floor(Date.now() / 1000) - 60, org: 'org-1', orgRole: 'OWNER',
    }));

    expect(runGuard()).toBe(false);
  });
});
