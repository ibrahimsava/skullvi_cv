import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { AudienceApi } from './audience-api';
import { AnalyticsOverview, PlatformTotals, Slice } from './audience.models';
import { AXIS, SERIES, categoricalFor, categoricalHoverFor, categorical } from './chart-palette';
import { CountUpDirective } from '../../../core/animations/count-up.directive';
import { RevealDirective } from '../../../core/animations/reveal.directive';

/** Fenêtres proposées. 7 jours pour réagir, 90 pour voir une tendance. */
const RANGES = [7, 30, 90] as const;

/**
 * Fréquentation du site, réservée à l'administrateur de la plateforme.
 *
 * <p>La page vivait dans l'espace recruteur, où le compte d'administration n'a jamais pu
 * entrer : n'appartenant à aucune organisation, la garde de cet espace le refusait. Elle est
 * désormais là où elle doit être.
 */
@Component({
  standalone: true,
  selector: 'app-audience',
  imports: [BaseChartDirective, CountUpDirective, RevealDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './audience.html',
  styleUrl: './audience.scss',
})
export class Audience implements OnInit {

  private readonly api = inject(AudienceApi);

  readonly ranges = RANGES;

  days = signal<number>(30);
  data = signal<AnalyticsOverview | null>(null);
  platform = signal<PlatformTotals | null>(null);
  loading = signal(true);
  loadError = signal(false);

  // ---------------------------------------------------------------- chargement

  ngOnInit(): void {
    this.load();
    this.api.platformTotals().subscribe({
      next: (t) => this.platform.set(t),
      error: () => this.platform.set(null),
    });
  }

  setRange(days: number): void {
    if (days === this.days()) return;
    this.days.set(days);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);

    this.api.platformOverview(this.days()).subscribe({
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
   * Courbes des visites et des visiteurs distincts.
   *
   * <p>Deux teintes franchement différentes, et un tracé pointillé pour la seconde : l'écart
   * entre les deux courbes est l'information utile, encore faut-il pouvoir les distinguer.
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
          borderColor: SERIES.visits.stroke,
          backgroundColor: SERIES.visits.fill,
          pointBackgroundColor: SERIES.visits.point,
          pointBorderColor: '#fff',
          pointBorderWidth: 1.5,
          borderWidth: 2.5,
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 5,
        },
        {
          label: 'Visiteurs distincts',
          data: d.series.map((p) => p.visitors),
          borderColor: SERIES.visitors.stroke,
          backgroundColor: SERIES.visitors.fill,
          pointBackgroundColor: SERIES.visitors.point,
          pointBorderColor: '#fff',
          pointBorderWidth: 1.5,
          borderWidth: 2,
          // Pointillé : distingue les deux courbes sans compter sur la seule couleur.
          borderDash: [5, 4],
          fill: false,
          tension: 0.3,
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
    animation: { duration: 800, easing: 'easeOutQuart' },
    plugins: {
      legend: {
        display: true, position: 'top', align: 'end',
        labels: {
          usePointStyle: true, pointStyle: 'line', boxWidth: 24, padding: 16,
          color: AXIS.label, font: { size: 12, weight: 600 },
        },
      },
      tooltip: {
        backgroundColor: AXIS.tooltipBg,
        padding: 11, cornerRadius: 6,
        titleFont: { size: 12, weight: 600 }, bodyFont: { size: 12 },
        usePointStyle: true,
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: AXIS.grid },
        ticks: { precision: 0, color: AXIS.tick, font: { size: 11 }, maxTicksLimit: 6 },
      },
      x: {
        border: { color: AXIS.border },
        grid: { display: false },
        ticks: { color: AXIS.tick, font: { size: 11 }, maxTicksLimit: 12, autoSkip: true },
      },
    },
  };

  // ---------------------------------------------------------------- histogramme

  /**
   * Pages les plus consultées.
   *
   * <p>Une couleur par barre, en barres horizontales pour que les chemins tiennent. Toutes
   * les barres dans la même teinte n'apportaient rien : la couleur ne distinguait plus rien.
   */
  readonly barData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    const n = d.topPages.length;
    return {
      labels: d.topPages.map((s) => s.label),
      datasets: [{
        label: 'Visites',
        data: d.topPages.map((s) => s.count),
        backgroundColor: categoricalFor(n),
        hoverBackgroundColor: categoricalHoverFor(n),
        borderRadius: 4,
        barThickness: 20,
      }],
    };
  });

  readonly barOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 800, easing: 'easeOutQuart' },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: AXIS.tooltipBg, padding: 10, cornerRadius: 6, displayColors: false,
        callbacks: { label: (c) => `${c.parsed.x} visite(s)` },
      },
    },
    scales: {
      x: {
        beginAtZero: true,
        border: { display: false },
        grid: { color: AXIS.grid },
        ticks: { precision: 0, color: AXIS.tick, font: { size: 11 }, maxTicksLimit: 6 },
      },
      y: {
        border: { display: false },
        grid: { display: false },
        ticks: { color: AXIS.label, font: { size: 11.5, weight: 500 } },
      },
    },
  };

  // ---------------------------------------------------------------- anneau

  /** Répartition des appareils, une teinte par catégorie. */
  readonly doughnutData = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    const n = d.devices.length;
    return {
      labels: d.devices.map((s) => s.label),
      datasets: [{
        data: d.devices.map((s) => s.count),
        backgroundColor: categoricalFor(n),
        hoverBackgroundColor: categoricalHoverFor(n),
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
    animation: { duration: 800, easing: 'easeOutQuart' },
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          usePointStyle: true, pointStyle: 'circle', boxWidth: 9, padding: 14,
          color: AXIS.label, font: { size: 12 },
        },
      },
      tooltip: { backgroundColor: AXIS.tooltipBg, padding: 10, cornerRadius: 6 },
    },
  };

  readonly hasDevices = computed(() => (this.data()?.devices.length ?? 0) > 0);
  readonly hasPages = computed(() => (this.data()?.topPages.length ?? 0) > 0);
  readonly hasTraffic = computed(() => (this.data()?.visits ?? 0) > 0);

  // ---------------------------------------------------------------- provenances

  /** Couleur de la barre d'une provenance, alignée sur la palette des graphiques. */
  sourceColor(index: number): string {
    return categorical(index);
  }

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

  // ---------------------------------------------------------------- indicateurs

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

  /**
   * Ton d'une étape de l'entonnoir.
   *
   * <p>Trois niveaux, et chacun a sa propre teinte : un entonnoir tout bleu ne dirait pas
   * où ça coince. Le libellé en pourcentage reste affiché à côté.
   */
  gaugeTone(rate: number): 'ok' | 'warn' | 'danger' {
    if (rate >= 40) return 'ok';
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
