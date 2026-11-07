import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../features/auth/auth';

/**
 * Accès à l'administration de la plateforme Skulvi.
 *
 * <p>À ne pas confondre avec l'espace recruteur, qui a sa propre garde
 * ({@code recruiterGuard}). Ce rôle-ci est celui de l'éditeur du service : il sert à gérer les
 * abonnements des clients, et ne donne accès à aucune de leurs données.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn() && auth.isPlatformAdmin()) {
    return true;
  }

  router.navigateByUrl('/', { replaceUrl: true });
  return false;
};
