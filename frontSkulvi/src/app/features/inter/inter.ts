import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Chemins de l'API accessibles sans jeton.
 *
 * <p>Tout ce qui est public vit maintenant sous `/public/` côté serveur. Un seul préfixe à
 * tester, au lieu d'une liste d'expressions régulières à maintenir en miroir des routes — c'est
 * précisément ce genre de duplication qui finit par se désynchroniser.
 */
const PUBLIC_PREFIXES = ['/auth/login', '/auth/register', '/auth/signup-organization', '/public/'];

/** Le dépôt de candidature est public, mais on y joint le jeton s'il y en a un. */
const PUBLIC_POST = /\/offers\/[^/]+\/applications$/;

function isPublic(url: string): boolean {
  const path = url.split('?')[0];
  return PUBLIC_PREFIXES.some((p) => path.includes(p));
}

/**
 * Intercepteur JWT global.
 *
 * <ol>
 *   <li>routes publiques : passent sans jeton — un jeton expiré oublié dans le navigateur
 *       ferait échouer une requête qui n'en demande pas ;
 *   <li>dépôt de candidature : le jeton est joint s'il est valide, pour rattacher la
 *       candidature au compte, mais son absence n'est pas une erreur ;
 *   <li>autres routes : jeton obligatoire, sinon redirection vers la connexion ;
 *   <li>réponse 401 : la session est effacée et l'utilisateur renvoyé à la connexion.
 * </ol>
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem('accessToken');

  if (isPublic(req.url)) {
    return next(req);
  }

  // Candidature : le jeton est un bonus, pas une exigence.
  const optionalAuth = req.method === 'POST' && PUBLIC_POST.test(req.url.split('?')[0]);

  if (!token) {
    if (optionalAuth) {
      return next(req);
    }
    router.navigate(['/login'], { queryParams: { suite: router.url } });
    return throwError(() => new Error('Non authentifié'));
  }

  const authorized = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(authorized).pipe(
    catchError((error) => {
      if (error.status === 401) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        router.navigate(['/login'], { queryParams: { suite: router.url } });
      }
      return throwError(() => error);
    })
  );
};
