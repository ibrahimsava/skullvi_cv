import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { AuthService } from '../../features/auth/auth';
import { adminGuard } from './admin-guard';

function fakeToken(payload: Record<string, unknown>): string {
  const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(payload)}.signature`;
}

const inOneHour = Math.floor(Date.now() / 1000) + 3600;

function runGuard() {
  return TestBed.runInInjectionContext(() =>
    adminGuard({} as ActivatedRouteSnapshot, { url: '/platform' } as RouterStateSnapshot));
}

describe('adminGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([]), AuthService] });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  it('laisse passer l\'administrateur de la plateforme', () => {
    localStorage.setItem('accessToken', fakeToken({ exp: inOneHour, roles: ['ADMIN'] }));
    expect(runGuard()).toBe(true);
  });

  /*
   * La distinction qui compte : le propriétaire d'un espace client n'est pas administrateur de
   * la plateforme. Confondre les deux lui donnerait accès aux abonnements des autres clients.
   */
  it('refuse le propriétaire d\'une organisation cliente', () => {
    localStorage.setItem('accessToken', fakeToken({
      exp: inOneHour, org: 'org-1', orgRole: 'OWNER', roles: ['RECRUTEUR', 'OWNER'],
    }));
    expect(runGuard()).toBe(false);
  });

  it('refuse un visiteur non connecté', () => {
    expect(runGuard()).toBe(false);
  });
});
