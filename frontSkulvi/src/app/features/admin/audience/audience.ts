import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { AuthService } from '../../auth/auth';
import { AudienceApi } from './audience-api';
import { AnalyticsOverview, PlatformTotals, Slice } from './audience.models';

/** Fenêtres proposées. 7 jours pour réagir, 90 pour voir une tendance. */
const RANGES = [7, 30, 90] as const;

@Component({
  standalone: true,
  selector: 'app-audience',
  imports: [BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './audience.html',
  styleUrl: './audience.scss',
})
export class Audience implements OnInit {

  private readonly api = inject(AudienceApi);
  private readonly auth = inject(AuthService);

  readonly ranges = RANGES;

  days = signal<number>(30);
  data = signal<AnalyticsOverview | null>(null);
  platform = signal<PlatformTotals | null>(null);
  loading = signal(true);
  loadError = signal(false);

  /**
   * L'administrateur de la plateforme voit tout le trafic du site ; un recruteur ne voit que
   * ses propres pages carrière et ses offres.
   */
  readonly isPlatformAdmin = computed(() => this.auth.isPlatformAdmin());

  // ---------------------------------------------------------------- chargement

  ngOnInit(): void {
    this.load();
    if (this.isPlatformAdmin()) {
      this.api.platformTotals().subscribe({
        next: (t) => this.platform.set(t),
        error: () => this.platform.set(null),
      });
    }
  }

  setRange(days: number): void {
    if (days === this.days()) return;
    this.days.set(days);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);

    const source$ = this.isPlatformAdmin()
      ? this.api.platformOverview(this.days())
      : this.api.overview(this.days());

    source$.subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  // ---------------------------------------------------------------- série temporelle

  /**
   * Courbe des visites et des visiteurs distincts.
   *
   * <p>Deux séries sur le même graphique, et l'écart entre elles est l'information utile :
   * des visites très au-dessus des visiteurs signifie que les gens reviennent.
   */
  readonly lineData = computed<ChartConfiguration<'line'>['data']>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    return {
      labels: d.series.map((p) => this.shortDay(p.day)),
      datasets: [
        {
          label: 'Visites',
          data: d.series.map((p) => p.visits),
          borderColor: '#1d4ed8',
          backgroundColor: 'rgba(29, 78, 216, 0.10)',
          pointBackgroundColor: '#1d4ed8',
          pointBorderColor: '#fff',
          pointBorderWidth: 1.5,
          borderWidth: 2.5,
          fill: true,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 5,
        },
        {
          label: 'Visiteurs distincts',
          data: d.series.map((p) => p.visitors),
          borderColor: '#5b8def',
          backgroundColor: 'transparent',
          pointBackgroundColor: '#5b8def',
          pointBorderColor: '#fff',
          pointBorderWidth: 1.5,
          borderWidth: 2,
          // Pointillés : distingue les deux courbes sans compter sur la seule couleur.
          borderDash: [5, 4],
          fill: false,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 5,
        },
      ],
    };
  });

  readonly lineOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    // Survoler n'importe où sur la colonne affiche les deux valeurs du jour.
    interaction: { mode: 'index', intersect: false },
    animation: { duration: 900, easing: 'easeOutQuart' },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        align: 'end',
        labels: { usePointStyle: true, pointStyle: 'line', boxWidth: 22, padding: 16,
                  color: '#3c4c69', font: { size: 12, weight: 600 } },
      },
      tooltip: {
        backgroundColor: '#0b1b3a',
        padding: 11,
        cornerRadius: 8,
        titleFont: { size: 12, weight: 600 },
        bodyFont: { size: 12 },
        displayColors: true,
        usePointStyle: true,
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: '#e4ecfb' },
        ticks: { precision: 0, color: '#6e7f9c', font: { size: 11 }, maxTicksLimit: 6 },
      },
      x: {
        border: { color: '#dbe4f5' },
        grid: { display: false },
        ticks: { color: '#6e7f9c', font: { size: 11 }, maxTicksLimit: 12, autoSkip: true },
      },
    },
  };

  // ---------------------------------------------------------------- histogramme

  /** Pages les plus consultées, en barres horizontales pour que les libellés tiennent. */
  readonly barData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    return {
      labels: d.topPages.map((s) => s.label),
      datasets: [{
        label: 'Visites',
        data: d.topPages.map((s) => s.count),
        backgroundColor: '#2f6bea',
        hoverBackgroundColor: '#1739a8',
        borderRadius: 5,
        barThickness: 18,
      }],
    };
  });

  readonly barOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 900, easing: 'easeOutQuart' },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#0b1b3a', padding: 10, cornerRadius: 8, displayColors: false,
        callbacks: { label: (c) => `${c.parsed.x} visite(s)` },
      },
    },
    scales: {
      x: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: '#e4ecfb' },
        ticks: { precision: 0, color: '#6e7f9c', font: { size: 11 }, maxTicksLimit: 6 },
      },
      y: {
        border: { display: false },
        grid: { display: false },
        ticks: { color: '#3c4c69', font: { size: 11.5, weight: 500 } },
      },
    },
  };

  // ---------------------------------------------------------------- anneau

  /** Répartition mobile / ordinateur. */
  readonly doughnutData = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    return {
      labels: d.devices.map((s) => s.label),
      datasets: [{
        data: d.devices.map((s) => s.count),
        backgroundColor: ['#1d4ed8', '#5b8def', '#c0d2ff'],
        hoverBackgroundColor: ['#1739a8', '#2f6bea', '#a8c4f7'],
        borderColor: '#fff',
        borderWidth: 3,
        hoverOffset: 6,
      }],
    };
  });

  readonly doughnutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    animation: { duration: 900, easing: 'easeOutQuart' },
    plugins: {
      legend: {
        position: 'bottom',
        labels: { usePointStyle: true, pointStyle: 'circle', boxWidth: 9, padding: 14,
                  color: '#3c4c69', font: { size: 12 } },
      },
      tooltip: { backgroundColor: '#0b1b3a', padding: 10, cornerRadius: 8 },
    },
  };

  readonly hasDevices = computed(() => (this.data()?.devices.length ?? 0) > 0);
  readonly hasPages = computed(() => (this.data()?.topPages.length ?? 0) > 0);
  readonly hasTraffic = computed(() => (this.data()?.visits ?? 0) > 0);

  // ---------------------------------------------------------------- jauges

  /**
   * Largeur d'une barre de répartition, normalisée sur la plus grande valeur.
   *
   * <p>Rapporter au total donnerait des barres toutes minuscules dès qu'il y a une dizaine
   * de provenances. Rapporter au maximum rend les écarts lisibles.
   */
  shareOfMax(slice: Slice, list: Slice[]): number {
    const max = Math.max(...list.map((s) => s.count), 1);
    return Math.round((slice.count / max) * 100);
  }

  /** Classe de ton d'une variation : vert si en hausse, rouge si en baisse. */
  trendTone(value: number | null): 'ok' | 'danger' | '' {
    if (value == null || value === 0) return '';
    return value > 0 ? 'ok' : 'danger';
  }

  trendLabel(value: number | null): string {
    if (value == null) return 'Pas de comparaison';
    const sign = value > 0 ? '+' : '';
    return `${sign}${value} % sur la période précédente`;
  }

  /** Taux borné à 100 pour la largeur d'une jauge. */
  gauge(rate: number): number {
    return Math.min(100, Math.max(0, Math.round(rate)));
  }

  gaugeTone(rate: number): '' | 'warn' | 'danger' {
    if (rate >= 40) return '';
    if (rate >= 15) return 'warn';
    return 'danger';
  }

  // ---------------------------------------------------------------- formats

  /** « 08/11 » : les étiquettes de l'axe doivent rester courtes. */
  private shortDay(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }

  rangeLabel(days: number): string {
    return days === 7 ? '7 jours' : days === 30 ? '30 jours' : '90 jours';
  }

  period(): string {
    const d = this.data();
    if (!d) return '';
    return `du ${this.frenchDate(d.from)} au ${this.frenchDate(d.to)}`;
  }

  private frenchDate(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }
}
