import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { forkJoin, of, switchMap, map, catchError } from 'rxjs';
import { AdminApi } from '../admin-api';
import { ApplicationSummary, DashboardStats } from '../admin.models';
import { RouterLink } from '@angular/router';


interface Kpi {
  label: string;
  value: number | string;
  alert?: boolean;
  /** Filtre à appliquer sur la page des candidatures quand l'indicateur est cliquable. */
  filter?: string;
}

@Component({
  standalone: true,
  selector: 'app-dashboard',
  imports: [BaseChartDirective, RouterLink],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly api = inject(AdminApi);

  stats = signal<DashboardStats | null>(null);
  trendCount = signal(0);
  chartData = signal<ChartConfiguration<'line'>['data']>({ labels: [], datasets: [] });

  kpis = computed<Kpi[]>(() => {
    const s = this.stats();
    if (!s) return [];
    return [
      { label: 'Candidatures reçues', value: s.applications },
      { label: 'Offres ouvertes', value: s.openOffers },
      { label: 'Candidatures analysées', value: s.scored },
      { label: 'Haute priorité', value: s.highPriority },
      {
        label: 'Score moyen',
        value: s.scored > 0 ? String(Math.round((s.averageScore ?? 0) * 10) / 10) : '—',
      },
      { label: 'Analyses en échec', value: s.failed, alert: s.failed > 0, filter: 'echec' },
    ];
  });

  /** Analyses consommées ce mois, face au quota du plan. */
  readonly quota = computed(() => {
    const s = this.stats();
    if (!s) return null;
    return {
      used: s.analysesUsed,
      limit: s.analysesLimit,
      label: s.analysesLimit == null ? 'Illimité' : String(s.analysesLimit),
      percent: s.analysesLimit ? Math.min(100, Math.round((s.analysesUsed / s.analysesLimit) * 100)) : 0,
    };
  });

  /**
   * Nombre d'analyses en attente ou en cours.
   *
   * <p>Affiché parce que l'attente est réelle : un seul CV est analysé à la fois, et chaque
   * analyse prend de l'ordre d'une minute. Sans ce chiffre, un recruteur qui vient de recevoir
   * dix candidatures croit que le service est en panne.
   */
  readonly pending = computed(() => this.stats()?.analysesPending ?? 0);

  chartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#1c2530',
        padding: 10,
        displayColors: false,
        callbacks: { label: (ctx) => `${ctx.parsed.y} candidature(s)` },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: '#e2e5e0' },
        ticks: { precision: 0, color: '#7a8592' },
      },
      x: {
        border: { color: '#cdd2cb' },
        grid: { display: false },
        ticks: { color: '#7a8592', maxTicksLimit: 10 },
      },
    },
  };

  ngOnInit(): void {
    this.api.getDashboard().subscribe((s) => this.stats.set(s));
    this.loadTrend();
  }

  private loadTrend(): void {
    this.api.getOffers().pipe(
      switchMap((offers) =>
        offers.length
          ? forkJoin(offers.map((o) =>
              this.api.getApplications(o.id).pipe(
                catchError(() => of([] as ApplicationSummary[]))
              )
            ))
          : of([] as ApplicationSummary[][])
      ),
      map((lists) => lists.flat())
    ).subscribe((apps) => {
      this.trendCount.set(apps.length);

      // Compte par jour (YYYY-MM-DD)
      const byDay = new Map<string, number>();
      for (const a of apps) {
        const day = a.submittedAt.substring(0, 10);
        byDay.set(day, (byDay.get(day) ?? 0) + 1);
      }

      // 30 derniers jours, jours sans candidature = 0
      const days: string[] = [];
      const today = new Date();
      for (let i = 29; i >= 0; i--) {
        const d = new Date(today);
        d.setDate(today.getDate() - i);
        days.push(d.toLocaleDateString('en-CA'));   // format YYYY-MM-DD, heure locale
      }

      this.chartData.set({
        labels: days.map((d) => `${d.slice(8, 10)}/${d.slice(5, 7)}`),
        datasets: [{
          data: days.map((d) => byDay.get(d) ?? 0),
          borderColor: '#0e5a52',
          backgroundColor: 'rgba(14, 90, 82, 0.10)',
          pointBackgroundColor: '#0e5a52',
          borderWidth: 2,
          fill: true,
          tension: 0.3,
          pointRadius: 3,
        }],
      });
    });
  }
}