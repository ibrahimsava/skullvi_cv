import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../features/auth/auth'; 


export const adminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn() && authService.isAdmin()) {
    return true; // L'admin peut passer
  }

  // Redirection si l'utilisateur n'est pas Admin
  router.navigateByUrl('/auth');
  return false;
};
