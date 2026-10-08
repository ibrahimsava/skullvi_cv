import { Routes } from '@angular/router';
import { Auth } from './features/auth/auth/auth';
import { Home } from './features/home/home';
import { Offres } from './features/offres/offres';
import { Form } from './features/offres/form/form';
import { adminGuard } from './core/guards/admin-guard';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'home', component: Home },
  { path: 'offres', component: Offres },
  { path: 'postuler', component: Auth },
  { path: 'analyse', component: Form },
  { path: 'login', component: Auth },
  { path: 'register', component: Auth },
  { path: 'auth', component: Auth },
  // 🔒 Seul l'admin peut accéder au formulaire de création d'offre désormais !
    { path: 'formoffre', component: Form,
     canActivate: [adminGuard] },
     { path: 'analyse', component: Form,
       canActivate: [adminGuard] },


];
