import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../features/auth/auth';

/**
 * En-tête des pages publiques.
 *
 * <p>Comble un manque réel : seule la page d'accueil avait une navigation, codée dans son
 * propre gabarit. Les pages offres, tarifs, inscription et candidature n'offraient aucune
 * sortie, hormis le bouton « Retour » flottant.
 *
 * <p>Le bouton d'action change selon la session : un visiteur voit l'essai gratuit, un
 * utilisateur connecté le raccourci vers son espace. Proposer « Créer un compte » à quelqu'un
 * déjà connecté est le genre de détail qui fait amateur.
 */
@Component({
  standalone: true,
  selector: 'app-site-header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './site-header.html',
  styleUrl: './site-header.scss',
})
export class SiteHeader {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Menu déroulant mobile. */
  open = signal(false);

  /** Passe le fond en opaque dès qu'on quitte le haut de page. */
  scrolled = signal(false);

  readonly connected = computed(() => this.auth.user() !== null && this.auth.isLoggedIn());
  readonly isRecruiter = computed(() => this.auth.isRecruiter());

  readonly spaceLink = computed(() => (this.isRecruiter() ? '/admin/dashboard' : '/mon-espace'));
  readonly spaceLabel = computed(() => (this.isRecruiter() ? 'Mon espace' : 'Mes candidatures'));

  constructor() {
    // Un menu mobile resté ouvert après une navigation masquerait la page d'arrivée.
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.open.set(false));
  }

  @HostListener('window:scroll')
  onScroll(): void {
    this.scrolled.set(window.scrollY > 8);
  }

  @HostListener('document:keydown.escape')
  close(): void {
    this.open.set(false);
  }

  toggle(): void {
    this.open.update((v) => !v);
  }
}
