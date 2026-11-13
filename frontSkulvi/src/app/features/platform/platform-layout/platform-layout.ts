import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth';

/**
 * Espace de l'administrateur de la plateforme.
 *
 * <p>Séparé de l'espace recruteur, et pas par confort : le compte de plateforme n'appartient
 * à <b>aucune</b> organisation, donc {@code recruiterGuard} lui refuse l'accès à
 * {@code /admin/**}. Les statistiques de fréquentation du site vivaient là-bas et lui étaient
 * donc inaccessibles.
 *
 * <p>Ce cloisonnement est volontaire et reste en place : l'administrateur voit des agrégats et
 * les abonnements, jamais les candidatures ni les CV d'un client.
 */
@Component({
  standalone: true,
  selector: 'app-platform-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './platform-layout.html',
  styleUrl: './platform-layout.scss',
})
export class PlatformLayout {

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly email = computed(() => this.auth.getUserEmail());

  readonly initial = computed(() => (this.email() ?? 'A').charAt(0).toUpperCase());

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }
}
