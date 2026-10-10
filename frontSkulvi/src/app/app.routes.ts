import { Routes } from '@angular/router';
import { Auth } from './features/auth/auth/auth';
import { Home } from './features/home/home';
import { Offres } from './features/offres/offres';
import { Form } from './features/offres/form/form';
import { adminGuard } from './core/guards/admin-guard';
import { AdminLayout } from './features/admin/admin-layout/admin-layout';
import { Dashboard } from './features/admin/dashboard/dashboard';
import { Candidatures } from './features/admin/candidatures/candidatures';
import { Postuler } from './features/postuler/postuler';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'home', component: Home },
  { path: 'offres', component: Offres },
  { path: 'analyse', component: Auth },
  { path: 'login', component: Auth },
  { path: 'register', component: Auth },
  { path: 'auth', component: Auth },

{ path: 'postuler/:offerId', component: Postuler },
{ path: 'postuler', redirectTo: 'offres', pathMatch: 'full' },

  // 🔒 Tout l'espace admin protégé par le guard
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'candidatures', component: Candidatures },
      { path: 'offres/new', component: Form },   // ton formulaire existant
    ],
  },
];