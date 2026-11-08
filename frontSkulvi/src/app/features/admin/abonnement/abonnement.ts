import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../auth/auth';
import { PlanCode } from '../../auth/model';
import { AdminApi } from '../admin-api';
import { PlanOption, UsageSnapshot } from '../admin.models';

/** Adresse de contact, utilisée quand le paiement en ligne n'est pas encore branché. */
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
  private readonly route = inject(ActivatedRoute);

  usage = signal<UsageSnapshot | null>(null);
  plans = signal<PlanOption[]>([]);
  loading = signal(true);
  loadError = signal(false);

  /** Le paiement en ligne est-il configuré sur cette instance ? */
  paymentOnline = signal(false);

  /** Plan dont la souscription est en cours : désactive le bouton le temps de la redirection. */
  busyPlan = signal<PlanCode | null>(null);
  error = signal('');

  /** Message de retour après un passage en caisse. */
  outcome = signal<'ok' | 'annule' | null>(null);

  readonly canManage = computed(() => this.auth.canManageOrganization());
  readonly organizationName = this.auth.organizationName;

  ngOnInit(): void {
    /*
     * Le paramètre de retour indique seulement d'où revient l'utilisateur ; il ne fait pas
     * foi sur le paiement. C'est le webhook signé qui change le plan, et la page recharge
     * donc la consommation depuis le serveur pour savoir où l'on en est réellement.
     */
    const paiement = this.route.snapshot.queryParamMap.get('paiement');
    if (paiement === 'ok' || paiement === 'annule') {
      this.outcome.set(paiement);
    }

    this.reload();

    this.api.getPlans().subscribe({
      next: (c) => this.plans.set(c.plans),
      error: () => this.plans.set([]),
    });

    this.api.billingStatus().subscribe({
      next: (s) => this.paymentOnline.set(s.online),
      error: () => this.paymentOnline.set(false),
    });
  }

  reload(): void {
    this.loading.set(true);
    this.api.getUsage().subscribe({
      next: (u) => {
        this.usage.set(u);
        this.loading.set(false);
        // Le plan a pu changer entre-temps : on resynchronise l'en-tête de l'application.
        this.auth.refreshUser().subscribe({ error: () => {} });
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  // ---------------------------------------------------------------- souscription

  /**
   * Lance le paiement d'un plan.
   *
   * <p>Redirige vers la page hébergée du prestataire : aucune donnée de carte ne transite
   * par l'application, ce qui nous laisse hors du périmètre de conformité bancaire.
   */
  subscribe(plan: PlanOption): void {
    if (this.busyPlan()) return;

    this.busyPlan.set(plan.plan);
    this.error.set('');

    this.api.startCheckout(plan.plan).subscribe({
      next: ({ url }) => {
        // Pas de remise à zéro de busyPlan : on quitte la page.
        window.location.href = url;
      },
      error: (err: HttpErrorResponse) => {
        this.busyPlan.set(null);
        this.error.set(this.messageFor(err));
      },
    });
  }

  /** Ouvre le portail du prestataire : carte, factures, résiliation. */
  openPortal(): void {
    this.error.set('');
    this.api.openBillingPortal().subscribe({
      next: ({ url }) => { window.location.href = url; },
      error: (err: HttpErrorResponse) => this.error.set(this.messageFor(err)),
    });
  }

  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 503) {
      return err.error?.detail
        ?? 'Le paiement en ligne n\'est pas disponible pour le moment. Écrivez-nous.';
    }
    if (err.status === 403) return 'Seul le propriétaire du compte gère l\'abonnement.';
    if (err.status === 409) return err.error?.detail ?? 'Aucun abonnement à gérer.';
    return 'Opération impossible pour le moment. Réessayez dans un instant.';
  }

  // ---------------------------------------------------------------- lecture

  /** Un plan sans plafond renvoie `null` : on l'écrit en mots plutôt qu'en chiffre. */
  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  euros(cents: number): string {
    return (cents / 100).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  }

  /**
   * Part du quota consommée, bornée à 100 %.
   *
   * <p>En dépassement, le ratio brut passerait au-delà de 100 et la barre déborderait. Le
   * dépassement est affiché à part, en euros, ce qui est l'information utile.
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
    if (u.status === 'PAST_DUE') return 'Paiement en attente';
    if (u.cancelAtPeriodEnd) return 'Résiliation programmée';
    return u.paid ? 'Abonnement actif' : 'Plan gratuit';
  }

  statusTone(u: UsageSnapshot): 'ok' | 'warn' | 'accent' {
    if (u.status === 'PAST_DUE') return 'warn';
    if (u.cancelAtPeriodEnd) return 'warn';
    return u.paid ? 'ok' : 'accent';
  }

  /** Bandeau en haut de page, ou chaîne vide s'il n'y a rien à signaler. */
  banner(u: UsageSnapshot): string {
    if (u.status === 'PAST_DUE') {
      return 'Votre dernier paiement a échoué. Mettez votre carte à jour depuis le portail '
        + 'de facturation. Votre compte reste utilisable aux limites du plan gratuit.';
    }
    if (u.cancelAtPeriodEnd && u.currentPeriodEnd) {
      return 'Votre abonnement prend fin à la date indiquée. Vous repasserez ensuite au plan '
        + 'gratuit, sans perdre vos offres ni vos candidatures.';
    }
    if (u.analysesLimit != null && u.analysesUsed >= u.analysesLimit) {
      if (u.overageCount > 0) {
        return `Vous avez dépassé votre quota mensuel de ${u.overageCount} analyse(s), `
          + 'facturées en supplément.';
      }
      return 'Vous avez atteint vos analyses gratuites du mois. Le compteur repart le 1er, '
        + 'ou choisissez un abonnement pour continuer tout de suite.';
    }
    return '';
  }

  bannerTone(u: UsageSnapshot): 'warn' | 'error' {
    return u.status === 'PAST_DUE' ? 'error' : 'warn';
  }

  /** Vrai si ce plan est au-dessus du plan courant. */
  isUpgrade(plan: PlanOption): boolean {
    const order: PlanCode[] = ['FREE', 'STARTER', 'PRO', 'BUSINESS'];
    const current = this.usage()?.plan;
    if (!current) return false;
    return order.indexOf(plan.plan) > order.indexOf(current);
  }

  /** Lien de contact prérempli, pour le cas où le paiement en ligne n'est pas branché. */
  mailto(plan?: PlanOption): string {
    const sujet = plan
      ? `Passer au plan ${plan.label} — ${this.organizationName() ?? ''}`
      : `Changer d'abonnement — ${this.organizationName() ?? ''}`;
    return `mailto:${SALES_EMAIL}?subject=${encodeURIComponent(sujet)}`;
  }
}
