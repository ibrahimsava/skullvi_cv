import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { OrgStatus, PlanCode } from '../../auth/model';

/** Une organisation cliente, vue depuis la plateforme. */
export interface ClientRow {
  id: string;
  name: string;
  slug: string;
  plan: PlanCode;
  planLabel: string;
  status: OrgStatus;
  paid: boolean;
  /** Prix mensuel du plan, en centimes. Calculé par le serveur. */
  priceCents: number;
  analysesUsed: number;
  analysesLimit: number | null;
  activeOffers: number;
  seats: number;
  createdAt: string;
}

/**
 * Liste des entreprises clientes et de leur consommation.
 *
 * <p>Permet d'activer un plan à la main : encaissement par virement, geste commercial, ou
 * dépannage. Les abonnements souscrits en ligne sont pilotés par le webhook, pas d'ici — et
 * forcer un plan sur un client qui a un abonnement actif le ferait revenir au prochain
 * événement du prestataire.
 */
@Component({
  standalone: true,
  selector: 'app-clients',
  imports: [DatePipe],
  templateUrl: './clients.html',
  styleUrl: './clients.scss',
})
export class Clients implements OnInit {

  private readonly http = inject(HttpClient);
  private readonly api = environment.apiBaseUrl;

  readonly plans: PlanCode[] = ['FREE', 'STARTER', 'PRO', 'BUSINESS'];

  rows = signal<ClientRow[]>([]);
  loading = signal(true);
  loadError = signal(false);
  busyId = signal<string | null>(null);
  toast = signal<{ text: string; error: boolean } | null>(null);

  search = signal('');

  readonly visible = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.rows()
      .filter((r) => !q || r.name.toLowerCase().includes(q) || r.slug.includes(q))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  });

  readonly paying = computed(() => this.rows().filter((r) => r.paid).length);

  /**
   * Revenu mensuel récurrent, en euros.
   *
   * <p>Somme des prix envoyés par le serveur avec chaque ligne. La grille vit dans
   * `Plan.java` : la recopier ici donnerait un chiffre faux au premier ajustement de tarif,
   * sans que rien ne le signale.
   */
  readonly mrr = computed(() =>
    Math.round(this.rows().reduce((sum, r) => sum + r.priceCents, 0) / 100));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.list().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  private list(): Observable<ClientRow[]> {
    return this.http.get<ClientRow[]>(`${this.api}/platform/organizations`);
  }

  // ---------------------------------------------------------------- actions

  changePlan(row: ClientRow, plan: string): void {
    if (plan === row.plan) return;

    if (row.paid && !confirm(
      `${row.name} a un abonnement en ligne actif.\n\n`
      + 'Forcer un plan ici sera écrasé au prochain événement du prestataire. '
      + 'Continuer quand même ?')) {
      this.load();   // remet la liste déroulante sur la valeur réelle
      return;
    }

    this.busyId.set(row.id);
    this.http.put(`${this.api}/platform/organizations/${row.id}/plan`, {}, { params: { plan } })
      .subscribe({
        next: () => {
          this.busyId.set(null);
          this.flash(`${row.name} est passé au plan ${plan}.`);
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.busyId.set(null);
          this.flash(err.error?.detail ?? 'Changement de plan impossible.', true);
          this.load();
        },
      });
  }

  // ---------------------------------------------------------------- affichage

  limitLabel(limit: number | null): string {
    return limit == null ? 'illimité' : String(limit);
  }

  /** Part du quota consommée. Zéro si le plan est illimité. */
  percent(row: ClientRow): number {
    if (row.analysesLimit == null || row.analysesLimit === 0) return 0;
    return Math.min(100, Math.round((row.analysesUsed / row.analysesLimit) * 100));
  }

  usageTone(row: ClientRow): 'ok' | 'warn' | 'danger' {
    const p = this.percent(row);
    if (p >= 90) return 'danger';
    if (p >= 70) return 'warn';
    return 'ok';
  }

  statusLabel(row: ClientRow): string {
    if (row.status === 'PAST_DUE') return 'Impayé';
    if (row.status === 'CANCELED') return 'Résilié';
    return row.paid ? 'Abonné' : 'Gratuit';
  }

  statusTone(row: ClientRow): 'ok' | 'warn' | 'danger' | '' {
    if (row.status === 'PAST_DUE') return 'danger';
    if (row.status === 'CANCELED') return 'warn';
    return row.paid ? 'ok' : '';
  }

  private flash(text: string, error = false): void {
    this.toast.set({ text, error });
    setTimeout(() => this.toast.set(null), 6000);
  }
}
