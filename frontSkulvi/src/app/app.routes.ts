import { Routes } from '@angular/router';
import { Auth } from './features/auth/auth/auth';
import { Home } from './features/home/home';
import { Offres } from './features/offres/offres';
import { Form } from './features/offres/form/form';
import { Tarifs } from './features/tarifs/tarifs';
import { SignupOrg } from './features/signup-org/signup-org';
import { Espace } from './features/espace/espace';
import { authGuard } from './core/guards/auth-guard';
import { recruiterGuard } from './core/guards/recruiter-guard';
import { AdminLayout } from './features/admin/admin-layout/admin-layout';
import { Dashboard } from './features/admin/dashboard/dashboard';
import { Candidatures } from './features/admin/candidatures/candidatures';
import { OffresAdmin } from './features/admin/offres-admin/offres-admin';
import { Abonnement } from './features/admin/abonnement/abonnement';
import { Audience } from './features/admin/audience/audience';
import { Equipe } from './features/admin/equipe/equipe';
import { Postuler } from './features/postuler/postuler';

export const routes: Routes = [
  // ---------------------------------------------------------------- public
  { path: '', component: Home },
  { path: 'home', component: Home },
  { path: 'offres', component: Offres },
  { path: 'tarifs', component: Tarifs },

  /** Page carrière d'une entreprise : `/c/cabinet-leon-et-fils`. Indexable, et chaque client
      la partage lui-même — c'est de l'acquisition gratuite. */
  { path: 'c/:slug', component: Offres },

  { path: 'inscription-entreprise', component: SignupOrg },

  { path: 'postuler/:offerId', component: Postuler },
  { path: 'postuler', redirectTo: 'offres', pathMatch: 'full' },

  // ---------------------------------------------------------------- authentification
  { path: 'login', component: Auth },
  { path: 'register', component: Auth },
  { path: 'auth', component: Auth },

  /** Ancien lien du menu, qui ouvrait la page de connexion sans raison apparente. */
  { path: 'analyse', redirectTo: 'offres', pathMatch: 'full' },

  // ---------------------------------------------------------------- espace personnel
  { path: 'mon-espace', component: Espace, canActivate: [authGuard] },

  // ---------------------------------------------------------------- espace recruteur
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [recruiterGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'offres', component: OffresAdmin },
      { path: 'offres/new', component: Form },
      { path: 'candidatures', component: Candidatures },
      { path: 'audience', component: Audience },
      { path: 'equipe', component: Equipe },
      { path: 'abonnement', component: Abonnement },
    ],
  },

  // Toute adresse inconnue ramène à l'accueil plutôt que sur une page blanche.
  { path: '**', redirectTo: '' },
];
