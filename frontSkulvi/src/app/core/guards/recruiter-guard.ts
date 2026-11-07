import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../features/auth/auth';

/**
 * Accès à l'espace recruteur.
 *
 * <p>Ce qui ouvre la porte, c'est l'appartenance à une organisation — pas un rôle « ADMIN »
 * global comme avant. Chaque client a maintenant son propre espace, et le rôle qu'il y occupe
 * détermine seulement ce qu'il peut y faire.
 *
 * <p>Un candidat connecté est renvoyé vers son espace à lui, pas vers la page de connexion :
 * il est bien authentifié, il s'est simplement trompé d'adresse.
 */
export const recruiterGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    router.navigate(['/login'], { queryParams: { suite: '/admin' } });
    return false;
  }

  if (!auth.hasOrganization()) {
    router.navigateByUrl('/mon-espace', { replaceUrl: true });
    return false;
  }

  return true;
};
