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
import { adminGuard } from './core/guards/admin-guard';
import { AdminLayout } from './features/admin/admin-layout/admin-layout';
import { Dashboard } from './features/admin/dashboard/dashboard';
import { Candidatures } from './features/admin/candidatures/candidatures';
import { OffresAdmin } from './features/admin/offres-admin/offres-admin';
import { Abonnement } from './features/admin/abonnement/abonnement';
import { PlatformLayout } from './features/platform/platform-layout/platform-layout';
import { Audience } from './features/platform/audience/audience';
import { Clients } from './features/platform/clients/clients';
import { Equipe } from './features/admin/equipe/equipe';
import { Postuler } from './features/postuler/postuler';
import { NotFound } from './features/not-found/not-found';
import { PageSeo } from './core/seo';

/**
 * Métadonnées de référencement, portées par les routes.
 *
 * <p>Posées ici et non dans chaque composant : une application d'une seule page garde le même
 * `index.html` partout, donc sans titre propre par route, les trente adresses du site se
 * présentent au moteur comme une seule et même page.
 *
 * <p><b>Chaque page vise des requêtes différentes.</b> Deux pages optimisées pour « logiciel
 * de tri de CV » se prennent mutuellement leur classement — c'est ce qu'on appelle la
 * cannibalisation, et c'est la façon la plus courante de mal se référencer en travaillant
 * beaucoup. L'accueil prend la requête générique, les tarifs prennent l'intention d'achat,
 * les offres prennent le visiteur candidat.
 *
 * <p>Les deux marchés visés — France et Côte d'Ivoire — partagent la langue. Les mots clés
 * sont donc communs, et c'est `hreflang` (posé par le service de référencement) qui déclare
 * les deux régions, plutôt que des pages séparées qui se concurrenceraient.
 */
const seo = (page: PageSeo) => ({ seo: page });

/** Pages d'un espace connecté : jamais dans un index, et sans intérêt pour un moteur. */
const PRIVE = seo({
  title: 'Espace privé',
  description: 'Accès réservé aux comptes Skulvi.',
  noindex: true,
});

export const routes: Routes = [
  // ---------------------------------------------------------------- public
  {
    path: '',
    component: Home,
    data: seo({
      title: 'Logiciel de tri de CV par intelligence artificielle',
      description: "Skulvi lit chaque CV, le note sur votre grille de critères et justifie "
        + "chaque point attribué. Essai gratuit sans limite de durée, pour les recruteurs en "
        + "France et en Côte d'Ivoire.",
      path: '/',
    }),
  },

  // Même contenu que « / » : canonique forcé sur la racine, sinon les deux adresses se
  // partagent l'autorité de la page d'accueil.
  {
    path: 'home',
    component: Home,
    data: seo({
      title: 'Logiciel de tri de CV par intelligence artificielle',
      description: "Skulvi lit chaque CV, le note sur votre grille de critères et justifie "
        + "chaque point attribué.",
      path: '/',
    }),
  },

  {
    path: 'offres',
    component: Offres,
    data: seo({
      title: "Offres d'emploi",
      description: "Toutes les offres d'emploi publiées par les entreprises qui recrutent avec "
        + "Skulvi. Candidature en ligne, réponse argumentée sur votre CV.",
      path: '/offres',
    }),
  },

  {
    path: 'tarifs',
    component: Tarifs,
    data: seo({
      title: 'Tarifs : à partir de 0 € par mois',
      description: "Plan gratuit permanent, puis 9 à 20 € par mois. Analyses de CV, offres et "
        + "utilisateurs inclus selon le plan. Sans engagement, résiliable en un clic.",
      path: '/tarifs',
    }),
  },

  /** Page carrière d'une entreprise : `/c/cabinet-leon-et-fils`. Indexable, et chaque client
      la partage lui-même — c'est de l'acquisition gratuite. Le titre réel est posé par le
      composant une fois l'entreprise chargée ; ceci n'est que le repli affiché pendant
      l'appel. */
  {
    path: 'c/:slug',
    component: Offres,
    data: seo({
      title: "Offres d'emploi de l'entreprise",
      description: "Les postes actuellement ouverts dans cette entreprise, et la candidature "
        + "en ligne.",
    }),
  },

  {
    path: 'inscription-entreprise',
    component: SignupOrg,
    data: seo({
      title: 'Créer un espace recruteur gratuit',
      description: "Ouvrez votre espace en deux minutes : 40 analyses de CV par mois, deux "
        + "offres ouvertes, sans carte bancaire et sans limite de durée.",
      path: '/inscription-entreprise',
    }),
  },

  /** Le titre réel vient de l'offre chargée : une annonce doit porter son propre intitulé,
      c'est lui qu'un candidat tape dans un moteur. */
  {
    path: 'postuler/:offerId',
    component: Postuler,
    data: seo({
      title: 'Postuler à cette offre',
      description: "Déposez votre CV et recevez une analyse argumentée de votre candidature.",
      ogType: 'article',
    }),
  },
  { path: 'postuler', redirectTo: 'offres', pathMatch: 'full' },

  // ---------------------------------------------------------------- authentification
  /*
   * `noindex` sur les pages de connexion : elles n'apportent rien dans un index, et une page
   * de formulaire qui remonte sur le nom de la marque détourne les visiteurs de l'accueil.
   */
  { path: 'login', component: Auth, data: seo({ title: 'Connexion', description: 'Accédez à votre espace Skulvi.', noindex: true }) },
  { path: 'register', component: Auth, data: seo({ title: 'Créer un compte candidat', description: 'Suivez vos candidatures et recevez l’analyse de votre CV.', noindex: true }) },
  { path: 'auth', component: Auth, data: seo({ title: 'Connexion', description: 'Accédez à votre espace Skulvi.', noindex: true }) },

  /** Ancien lien du menu, qui ouvrait la page de connexion sans raison apparente. */
  { path: 'analyse', redirectTo: 'offres', pathMatch: 'full' },

  // ---------------------------------------------------------------- espace personnel
  { path: 'mon-espace', component: Espace, canActivate: [authGuard], data: PRIVE },

  // ---------------------------------------------------------------- espace recruteur
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [recruiterGuard],
    data: PRIVE,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'offres', component: OffresAdmin },
      { path: 'offres/new', component: Form },
      { path: 'candidatures', component: Candidatures },
      { path: 'equipe', component: Equipe },
      { path: 'abonnement', component: Abonnement },
    ],
  },

  // ---------------------------------------------------------------- plateforme
  /*
   * Espace de l'administrateur Skulvi. Séparé de /admin parce que ce compte n'appartient
   * à aucune organisation : `recruiterGuard` lui refuserait l'entrée. Les statistiques de
   * fréquentation du site vivent ici, et nulle part ailleurs.
   */
  {
    path: 'platform',
    component: PlatformLayout,
    canActivate: [adminGuard],
    data: PRIVE,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'audience' },
      { path: 'audience', component: Audience },
      { path: 'clients', component: Clients },
    ],
  },

  /*
   * Page introuvable, et non plus une redirection vers l'accueil.
   *
   * <p>Renvoyer le contenu de l'accueil sur une adresse inexistante est une « soft 404 » : le
   * moteur indexe une page qui n'existe pas, puis finit par se méfier du domaine entier. Un
   * visiteur, lui, se retrouvait sur l'accueil sans comprendre pourquoi.
   *
   * <p>Le serveur répond tout de même 200, comme pour toute application d'une seule page — ce
   * que corrige le `noindex`, qui est le signal que le moteur écoute vraiment.
   */
  {
    path: '**',
    component: NotFound,
    data: seo({
      title: 'Page introuvable',
      description: "Cette adresse n'existe pas ou n'existe plus.",
      noindex: true,
    }),
  },
];
