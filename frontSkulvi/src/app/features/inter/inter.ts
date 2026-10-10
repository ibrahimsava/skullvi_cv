import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

// Endpoints publics de l'API : ces requêtes ne doivent pas nécessiter un token JWT.
// CHANGÉ : ce sont maintenant de vrais chemins de l'API (/auth/...), et non des routes Angular.
const PUBLIC_URLS = ['/auth/login', '/auth/register', '/auth/forgot-password'];

// NOUVEAU : les offres sont publiques en LECTURE seulement.
//   publics   : GET /offers  et  GET /offers/{id}
//   protégés  : /offers/{id}/ranking, /offers/{id}/applications, POST /offers, POST /offers/{id}/close
const PUBLIC_OFFERS_GET = /\/offers(\/[^/]+)?$/;

function isPublic(method: string, url: string): boolean {
  const path = url.split('?')[0];
  if (PUBLIC_URLS.some((u) => path.includes(u))) return true;
  return method === 'GET' && PUBLIC_OFFERS_GET.test(path);
}

// Intercepteur JWT global : il est exécuté avant chaque requête HTTP de l'application.
// Son rôle est de :
// 1) laisser passer les routes publiques, sans token
// 2) rediriger vers /auth si une route protégée est appelée sans token
// 3) ajouter Authorization: Bearer <token> si le token est présent
// 4) supprimer le token et rediriger si le backend répond 401
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem('accessToken');
  const isPublicRequest = isPublic(req.method, req.url);

  // NOUVEAU : route publique → on laisse passer SANS ajouter le token.
  // Un token expiré resté dans le navigateur ferait sinon échouer la requête côté Spring.
  if (isPublicRequest) {
    return next(req);
  }

  // Si aucun token et que la requête n'est pas publique, on bloque la route protégée.
  if (!token) {
    router.navigateByUrl('/auth');
    return throwError(() => new Error('Unauthorized'));
  }

  // Si un token existe, on clone la requête pour ajouter le header Authorization.
  const clonedRequest = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  // Si le backend répond 401, on supprime le token et on redirige vers le login.
  return next(clonedRequest).pipe(
    catchError((error) => {
      // CHANGÉ : plus besoin de tester !isPublicRequest, les routes publiques sont déjà sorties plus haut
      if (error.status === 401) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        router.navigateByUrl('/auth');
      }

      return throwError(() => error);
    })
  );
};