import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AdminApi } from '../admin-api';
import { PlanOption, UsageSnapshot } from '../admin.models';
import { AuthService } from '../../auth/auth';

/** Adresse de contact pour un changement de plan, tant que le paiement en ligne n'est pas branché. */
const SALES_EMAIL = 'contact@skulvi.fr';

@Component({
  standalone: true,
  selector: 'app-abonnement',
  imports: [DatePipe],
  templateUrl: './abonnement.html',
  styleUrl: './abonnement.scss',
})
export class Abonnement implements OnInit {

  private readonly api = inject(AdminApi);
  private readonly auth = inject(AuthService);

  usage = signal<UsageSnapshot | null>(null);
  plans = signal<PlanOption[]>([]);
  loading = signal(true);
  loadError = signal(false);

  readonly canManage = computed(() => this.auth.canManageOrganization());
  readonly organizationName = this.auth.organizationName;

  ngOnInit(): void {
    this.api.getUsage().subscribe({
      next: (u) => {
        this.usage.set(u);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });

    this.api.getPlans().subscribe({
      next: (c) => this.plans.set(c.plans),
      error: () => this.plans.set([]),
    });
  }

  // ---------------------------------------------------------------- lecture

  /** Un plan sans plafond renvoie `null` : on l'écrit en mots plutôt qu'en chiffre. */
  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  euros(cents: number): string {
    return (cents / 100).toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  /**
   * Part du quota consommée, bornée à 100 %.
   *
   * <p>En dépassement, le ratio brut passerait au-delà de 100 et la barre déborderait de son
   * conteneur. Le dépassement est affiché à part, en euros, ce qui est l'information utile.
   */
  percent(used: number, limit: number | null): number {
    if (limit == null || limit === 0) return 0;
    return Math.min(100, Math.round((used / limit) * 100));
  }

  meterTone(used: number, limit: number | null): '' | 'warn' | 'danger' {
    const p = this.percent(used, limit);
    if (p >= 100) return 'danger';
    if (p >= 80) return 'warn';
    return '';
  }

  statusLabel(u: UsageSnapshot): string {
    switch (u.status) {
      case 'TRIAL': return "Période d'essai";
      case 'ACTIVE': return 'Abonnement actif';
      case 'PAST_DUE': return 'Paiement en attente';
      case 'SUSPENDED': return 'Compte en lecture seule';
    }
  }

  statusTone(u: UsageSnapshot): 'ok' | 'warn' | 'danger' | 'accent' {
    switch (u.status) {
      case 'ACTIVE': return 'ok';
      case 'TRIAL': return 'accent';
      case 'PAST_DUE': return 'warn';
      case 'SUSPENDED': return 'danger';
    }
  }

  /** Message d'alerte en haut de page, ou chaîne vide s'il n'y a rien à signaler. */
  banner(u: UsageSnapshot): string {
    if (u.status === 'SUSPENDED') {
      return "Votre compte est en lecture seule : vos données sont intactes, mais vous ne pouvez "
        + 'plus ouvrir d\'offre ni analyser de CV. Choisissez un abonnement pour reprendre.';
    }
    if (u.status === 'PAST_DUE') {
      return 'Votre dernier paiement a échoué. Régularisez pour éviter la suspension du compte.';
    }
    if (u.status === 'TRIAL' && u.trialDaysLeft != null && u.trialDaysLeft <= 3) {
      return u.trialDaysLeft === 0
        ? "Votre essai se termine aujourd'hui."
        : `Votre essai se termine dans ${u.trialDaysLeft} jour(s).`;
    }
    if (u.analysesLimit != null && u.analysesUsed >= u.analysesLimit) {
      return u.overageCount > 0
        ? `Vous avez dépassé votre quota mensuel de ${u.overageCount} analyse(s), facturées en supplément.`
        : 'Vous avez atteint votre quota mensuel d\'analyses.';
    }
    return '';
  }

  bannerTone(u: UsageSnapshot): 'warn' | 'error' {
    return u.status === 'SUSPENDED' ? 'error' : 'warn';
  }

  /** Lien de contact prérempli : plus simple à traiter qu'un e-mail vide. */
  mailto(plan?: PlanOption): string {
    const sujet = plan
      ? `Passer au plan ${plan.label} — ${this.organizationName() ?? ''}`
      : `Changer d'abonnement — ${this.organizationName() ?? ''}`;
    return `mailto:${SALES_EMAIL}?subject=${encodeURIComponent(sujet)}`;
  }
}
