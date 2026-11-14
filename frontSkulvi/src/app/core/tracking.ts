import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { environment } from '../../environments/environment';

/** Nature de l'événement signalé, en miroir de l'enum du serveur. */
export type TrackKind =
  | 'PAGE' | 'OFFER_VIEW' | 'APPLY_START' | 'APPLY_DONE'
  | 'SIGNUP_ORG' | 'SIGNUP_CANDIDATE';

interface TrackPayload {
  path: string;
  referrer?: string;
  kind?: TrackKind;
  offerId?: string;
  orgSlug?: string;
}

/**
 * Mesure d'audience, côté navigateur.
 *
 * <p>Sans cookie et sans identifiant local : le serveur calcule une empreinte quotidienne
 * non réversible depuis l'adresse et le navigateur. Rien n'est écrit sur l'appareil du
 * visiteur, ce qui évite le bandeau de consentement.
 *
 * <p>Deux précautions côté client :
 *
 * <ul>
 *   <li>les erreurs sont avalées : une statistique perdue est sans conséquence, une page
 *       cassée parce que le compteur a échoué serait inacceptable ;
 *   <li>l'espace recruteur et l'espace candidat ne sont pas mesurés. Compter les pages
 *       internes d'un utilisateur connecté serait du suivi d'usage, pas de l'audience — et
 *       noierait les chiffres qui intéressent vraiment.
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class Tracking {

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = environment.apiBaseUrl;

  /**
   * Préfixes exclus de la mesure : espaces privés.
   *
   * <p>`/platform` en fait partie, et c'est le plus important des trois : c'est la page où
   * l'administrateur consulte la fréquentation du site. Sans cette exclusion, chacune de ses
   * visites s'ajoute aux chiffres qu'il est précisément en train de lire — un compteur qui
   * compte celui qui le regarde.
   */
  private readonly privatePrefixes = ['/admin', '/platform', '/mon-espace'];

  /** Dernier chemin signalé, pour ne pas compter deux fois la même page. */
  private lastPath = '';

  /**
   * Démarre le suivi des changements de page.
   *
   * <p>Appelé une fois au démarrage de l'application. S'abonne aux navigations réussies
   * plutôt qu'aux clics : une redirection de garde ne doit pas compter comme une visite de
   * la page demandée.
   */
  start(): void {
    // La première page ne passe pas par NavigationEnd si l'application démarre dessus.
    this.page(this.router.url);

    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.page(e.urlAfterRedirects));
  }

  /** Signale l'affichage d'une page. */
  page(url: string): void {
    const path = url.split(/[?#]/)[0] || '/';
    if (this.isPrivate(path) || path === this.lastPath) {
      return;
    }
    this.lastPath = path;
    this.send({ path, kind: 'PAGE', referrer: document.referrer || undefined });
  }

  /** Signale la consultation d'une offre : première étape de l'entonnoir. */
  offerView(offerId: string): void {
    this.send({ path: '/offres/:id', kind: 'OFFER_VIEW', offerId });
  }

  /** Signale l'ouverture du formulaire de candidature. */
  applyStart(offerId: string): void {
    this.send({ path: '/postuler/:id', kind: 'APPLY_START', offerId });
  }

  /** Signale la consultation d'une page carrière d'entreprise. */
  careerPage(slug: string): void {
    this.send({ path: '/c/:slug', kind: 'PAGE', orgSlug: slug, referrer: document.referrer || undefined });
  }

  // ---------------------------------------------------------------- interne

  private isPrivate(path: string): boolean {
    return this.privatePrefixes.some((p) => path.startsWith(p));
  }

  private send(payload: TrackPayload): void {
    this.http.post(`${this.api}/public/track`, payload).subscribe({
      next: () => {},
      // Volontairement silencieux : hors ligne, bloqueur de publicité, limite de débit
      // atteinte — aucun de ces cas ne concerne le visiteur.
      error: () => {},
    });
  }
}
