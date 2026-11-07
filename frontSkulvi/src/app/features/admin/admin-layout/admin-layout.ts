import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth';
import { AdminApi } from '../admin-api';
import { UsageSnapshot } from '../admin.models';

@Component({
  standalone: true,
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout implements OnInit {

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly api = inject(AdminApi);

  collapsed = signal(this.readCollapsed());
  usage = signal<UsageSnapshot | null>(null);

  readonly user = this.auth.user;
  readonly organizationName = this.auth.organizationName;
  readonly canManage = computed(() => this.auth.canManageOrganization());

  readonly email = computed(() => this.auth.getUserEmail());

  readonly initial = computed(() => {
    const u = this.user();
    return (u?.firstName || u?.email || 'S').charAt(0).toUpperCase();
  });

  readonly roleLabel = computed(() => {
    switch (this.user()?.orgRole) {
      case 'OWNER': return 'Propriétaire';
      case 'RECRUITER': return 'Recruteur';
      case 'VIEWER': return 'Lecteur';
      default: return 'Membre';
    }
  });

  /**
   * Bandeau affiché en haut de l'espace, ou chaîne vide.
   *
   * <p>Il n'apparaît que quand il y a une action à mener : essai bientôt fini, quota atteint,
   * compte bloqué. Un bandeau permanent finit par ne plus être lu.
   */
  readonly banner = computed(() => {
    const u = this.usage();
    if (!u) return '';

    if (u.status === 'SUSPENDED') {
      return 'Votre compte est en lecture seule. Choisissez un abonnement pour reprendre vos recrutements.';
    }
    if (u.status === 'PAST_DUE') {
      return 'Votre dernier paiement a échoué. Régularisez pour éviter la suspension.';
    }
    if (u.status === 'TRIAL' && u.trialDaysLeft != null && u.trialDaysLeft <= 5) {
      return u.trialDaysLeft === 0
        ? "Dernier jour de votre essai gratuit."
        : `Il reste ${u.trialDaysLeft} jour(s) d'essai gratuit.`;
    }
    if (u.analysesLimit != null && u.analysesUsed >= u.analysesLimit) {
      return 'Quota mensuel d\'analyses atteint.';
    }
    return '';
  });

  readonly bannerUrgent = computed(() => {
    const status = this.usage()?.status;
    return status === 'SUSPENDED' || status === 'PAST_DUE';
  });

  ngOnInit(): void {
    // L'identité stockée peut dater d'une session précédente : on la resynchronise.
    this.auth.refreshUser().subscribe({ error: () => {} });

    this.api.getUsage().subscribe({
      next: (u) => this.usage.set(u),
      error: () => this.usage.set(null),
    });
  }

  toggle(): void {
    this.collapsed.update((v) => !v);
    try {
      localStorage.setItem('adminSidebarCollapsed', String(this.collapsed()));
    } catch { /* stockage indisponible */ }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }

  private readCollapsed(): boolean {
    try { return localStorage.getItem('adminSidebarCollapsed') === 'true'; }
    catch { return false; }
  }
}
