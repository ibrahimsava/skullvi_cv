import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AudienceApi } from './audience-api';
import { AnalyticsOverview, Metric, PlatformTotals, Slice } from './audience.models';
import { categorical } from './chart-palette';
import { CountUpDirective } from '../../../core/animations/count-up.directive';
import { RevealDirective } from '../../../core/animations/reveal.directive';
import { Sparkline } from '../../../core/charts/sparkline';

/** Fenêtres proposées. 7 jours pour réagir, 90 pour voir une tendance. */
const RANGES = [7, 30, 90] as const;

/**
 * Couleur par indicateur.
 *
 * <p>Posée sur la clé et non sur la position : si l'ordre des vignettes change un jour, la
 * couleur suit son indicateur. Les visites et les visiteurs partagent la teinte bleue — ce
 * sont deux lectures de la même chose — tandis que l'entonnoir prend des teintes
 * progressives, et les inscriptions une couleur franchement distincte : c'est le seul
 * indicateur qui compte des personnes, pas des événements.
 */
const COULEURS: Record<string, string> = {
  visits: categorical(0),
  visitors: categorical(0),
  offer_view: categorical(1),
  apply_start: categorical(2),
  apply_done: categorical(3),
  signup_org: categorical(4),
  signup_candidate: categorical(5),
};

/**
 * Fréquentation du site, réservée à l'administrateur de la plateforme.
 *
 * <p>Présentée en « petits multiples » : une vignette par indicateur, toutes alignées sur le
 * même axe de temps, chacune avec son nombre, sa variation et sa micro-courbe.
 *
 * <p>Pourquoi cette forme plutôt qu'un grand graphique entouré de cartes. Un seul graphique
 * impose une échelle commune, et des grandeurs d'ordres différents — un millier de visites
 * contre une dizaine d'inscriptions — y sont illisibles : la petite série est écrasée sur
 * l'axe. Les vignettes, elles, se lisent par comparaison de <i>formes</i>, et l'ordre suit le
 * parcours réel d'un visiteur : il arrive, consulte une offre, ouvre le formulaire, envoie sa
 * candidature. Parcourir la grille revient donc à suivre l'entonnoir, et une chute se repère
 * sans calcul.
 *
 * <p>Aucune bibliothèque de graphiques : tout est en SVG et en CSS. Les courbes sont nettes
 * sur les écrans à forte densité, là où un {@code canvas} est flou, et la page ne charge plus
 * rien pour les dessiner.
 */
@Component({
  standalone: true,
  selector: 'app-audience',
  imports: [CountUpDirective, RevealDirective, Sparkline],
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

  // ---------------------------------------------------------------- vignettes

  readonly metrics = computed<Metric[]>(() => this.data()?.metrics ?? []);

  couleur(m: Metric): string {
    return COULEURS[m.key] ?? categorical(0);
  }

  /**
   * Plus haute valeur journalière de l'indicateur.
   *
   * <p>Affichée sous la courbe, et c'est indispensable : chaque vignette ayant sa propre
   * échelle, une courbe qui monte haut ne dit rien de la grandeur. Sans ce repère, deux
   * vignettes d'allure identique pourraient valoir 3 et 3 000.
   */
  pic(m: Metric): number {
    return m.daily.length ? Math.max(...m.daily) : 0;
  }

  /** Moyenne par jour, arrondie : résume la vignette en un seul chiffre comparable. */
  parJour(m: Metric): string {
    if (!m.daily.length) return '0';
    const moyenne = m.total / m.daily.length;
    return moyenne >= 10 ? String(Math.round(moyenne)) : moyenne.toFixed(1);
  }

  trendTone(value: number | null): 'ok' | 'danger' | '' {
    if (value == null || value === 0) return '';
    return value > 0 ? 'ok' : 'danger';
  }

  /** « +12 % », « −3 % », ou un tiret quand il n'y a rien à comparer. */
  trendLabel(value: number | null): string {
    if (value == null) return '—';
    const signe = value > 0 ? '+' : value < 0 ? '−' : '';
    return `${signe}${Math.abs(value)} %`;
  }

  // ---------------------------------------------------------------- répartitions

  /** Couleur d'une barre de répartition, alignée sur la palette des vignettes. */
  sourceColor(index: number): string {
    return categorical(index);
  }

  /**
   * Largeur d'une barre, normalisée sur la plus grande valeur de la liste.
   *
   * <p>Rapporter au total donnerait des barres toutes minuscules dès qu'il y a une dizaine
   * d'entrées. Rapporter au maximum rend les écarts lisibles.
   */
  shareOfMax(slice: Slice, list: Slice[]): number {
    const max = Math.max(...list.map((s) => s.count), 1);
    return Math.round((slice.count / max) * 100);
  }

  readonly hasDevices = computed(() => (this.data()?.devices.length ?? 0) > 0);
  readonly hasPages = computed(() => (this.data()?.topPages.length ?? 0) > 0);
  readonly hasTraffic = computed(() => (this.data()?.visits ?? 0) > 0);

  // ---------------------------------------------------------------- entonnoir

  /** Taux borné à 100 pour la largeur d'une jauge. */
  gauge(rate: number): number {
    return Math.min(100, Math.max(0, Math.round(rate)));
  }

  /**
   * Ton d'une étape de l'entonnoir.
   *
   * <p>Trois niveaux, chacun sa teinte : un entonnoir tout bleu ne dirait pas où ça coince.
   * Le pourcentage reste affiché à côté, pour que la couleur ne porte pas seule le message.
   */
  gaugeTone(rate: number): 'ok' | 'warn' | 'danger' {
    if (rate >= 40) return 'ok';
    if (rate >= 15) return 'warn';
    return 'danger';
  }

  // ---------------------------------------------------------------- formats

  /** Bornes de la période, pour l'axe commun affiché sous la grille. */
  readonly axeDebut = computed(() => this.frenchShort(this.data()?.from));
  readonly axeFin = computed(() => this.frenchShort(this.data()?.to));

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

  private frenchShort(iso?: string): string {
    if (!iso) return '';
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }
}
