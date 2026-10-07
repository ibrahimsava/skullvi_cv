import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

// Ce tableau contient les endpoints publics.
// Ces requêtes ne doivent pas nécessiter un token JWT pour être envoyées.
const PUBLIC_URLS = ['/login', '/register', '/forgot-password'];

// Intercepteur JWT global : il est exécuté avant chaque requête HTTP de l'application.
// Son rôle est de :
// 1) vérifier si le token existe dans le localStorage
// 2) ajouter Authorization: Bearer <token> si le token est présent
// 3) laisser passer les routes publiques sans token
// 4) rediriger vers /auth si une route protégée est appelée sans token
export const authInterceptor: HttpInterceptorFn = (req, next) => {
    
  const router = inject(Router);
  const token = localStorage.getItem('accessToken');
  const isPublicRequest = PUBLIC_URLS.some((url) => req.url.includes(url));

  // Si aucun token et que la requête n'est pas publique, on bloque la route protégée.
  if (!token && !isPublicRequest) {
    router.navigateByUrl('/auth');
    return throwError(() => new Error('Unauthorized'));
  }

  // Si pas de token mais requête publique, on laisse passer la requête normalement.
  if (!token) {
    return next(req);
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
      if (error.status === 401 && !isPublicRequest) {
        localStorage.removeItem('accessToken');
        router.navigateByUrl('/auth');
      }

      return throwError(() => error);
    })
  );
};
