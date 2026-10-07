import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { routes } from './app.routes';
import { authInterceptor } from './features/inter/inter';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    // Gère les erreurs globales du navigateur et affiche les exceptions côté frontend.
    provideBrowserGlobalErrorListeners(),

    // Active le système de navigation et charge les routes de l'application.
    provideRouter(routes),

    // Charge les modules Angular nécessaires aux formulaires comme ngModel et ngForm.
    importProvidersFrom(FormsModule),

    // Active HttpClient et enregistre l'intercepteur JWT pour toutes les requêtes HTTP.
    provideHttpClient(withInterceptors([authInterceptor])),
  ]
};
