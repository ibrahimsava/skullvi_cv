import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SiteHeader } from '../../../layout/site-header/site-header';
import { AuthService } from '../auth';
import { AuthResponse, UserRequest } from '../model';

/**
 * Connexion et inscription candidat.
 *
 * <p>La création d'un espace recruteur a sa propre page ({@code /inscription-entreprise}) :
 * elle demande le nom de l'entreprise et s'adresse à quelqu'un qui vient acheter, pas
 * postuler. Mélanger les deux parcours dans un même formulaire perdait tout le monde.
 */
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, SiteHeader],
  selector: 'app-auth',
  styleUrl: './auth.scss',
  templateUrl: './auth.html',
})
export class Auth implements OnInit {

  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  mode = signal<'login' | 'register'>('login');
  loading = signal(false);
  isError = signal(false);
  message = signal('');

  userRequest: UserRequest = { firstName: '', lastName: '', email: '', password: '' };

  /** Page demandée avant la redirection vers la connexion. */
  private redirectTo = '';

  ngOnInit(): void {
    const path = this.route.snapshot.routeConfig?.path ?? '';
    this.mode.set(path === 'register' ? 'register' : 'login');
    this.redirectTo = this.route.snapshot.queryParamMap.get('suite') ?? '';
  }

  get isLogin(): boolean {
    return this.mode() === 'login';
  }

  submit(): void {
    if (this.loading()) return;

    this.isError.set(false);
    this.message.set('');
    this.loading.set(true);

    const call$ = this.isLogin
      ? this.authService.login(this.userRequest.email, this.userRequest.password)
      : this.authService.createUser(this.userRequest);

    call$.subscribe({
      next: (res) => {
        this.loading.set(false);
        this.goAfterLogin(res);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.isError.set(true);
        this.message.set(this.messageFor(err));
      },
    });
  }

  /**
   * Oriente vers le bon espace après connexion.
   *
   * <p>La destination d'un recruteur vient de la réponse du serveur, et non d'un rôle lu dans
   * le jeton : `organizationId` dit sans ambiguïté si ce compte a un espace recruteur.
   * L'ancienne version testait un rôle « ADMIN » qui n'existe plus que pour l'administrateur
   * de la plateforme, et aurait envoyé tous les recruteurs sur la liste des offres publiques.
   *
   * <p>L'administrateur de la plateforme passe en premier, et c'est le seul cas où le rôle du
   * jeton tranche : n'appartenant à aucune organisation, il n'a pas d'`organizationId` et
   * tombait donc dans l'espace candidat — qui ne lui sert à rien et où les statistiques de
   * fréquentation semblaient avoir disparu. L'en-tête, lui, pointait déjà au bon endroit : la
   * seule chose qui manquait était cette branche.
   */
  private goAfterLogin(res: AuthResponse): void {
    if (this.redirectTo && this.redirectTo.startsWith('/')) {
      this.router.navigateByUrl(this.redirectTo);
      return;
    }
    if (this.authService.isPlatformAdmin()) {
      this.router.navigateByUrl('/platform/audience');
      return;
    }
    if (res.user?.organizationId) {
      this.router.navigateByUrl('/admin/dashboard');
      return;
    }
    this.router.navigateByUrl('/mon-espace');
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 401) return 'E-mail ou mot de passe incorrect.';
    if (err.status === 409) return 'Cette adresse e-mail est déjà utilisée.';
    if (err.status === 400) return 'Vérifiez les champs : 8 caractères minimum pour le mot de passe.';
    return this.isLogin
      ? 'Connexion impossible pour le moment. Réessayez dans un instant.'
      : 'La création du compte a échoué. Réessayez dans un instant.';
  }
}
