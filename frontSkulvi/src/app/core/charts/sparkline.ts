import { Component, computed, input } from '@angular/core';

/**
 * Micro-courbe d'une vignette d'indicateur.
 *
 * <p>Dessinée en SVG et non en {@code canvas}, pour trois raisons concrètes :
 *
 * <ul>
 *   <li><b>netteté</b> : un canvas est tramé à la résolution logique, donc flou sur un écran
 *       à forte densité, ce qui se voit cruellement sur un trait d'un pixel ;
 *   <li><b>coût</b> : sept vignettes feraient sept contextes de dessin et sept boucles
 *       d'animation, pour une courbe de trente points ;
 *   <li><b>simplicité</b> : aucune bibliothèque, donc rien à charger et rien à configurer.
 * </ul>
 *
 * <p><b>Chaque vignette a sa propre échelle verticale.</b> C'est le principe des petits
 * multiples : on compare des <i>formes</i>, pas des hauteurs. Mille visites et dix
 * inscriptions sur un axe commun écraseraient la seconde courbe à plat, et on perdrait
 * justement ce qu'on cherche à voir. Le maximum est donc affiché à côté, sinon la hauteur ne
 * voudrait rien dire.
 */
@Component({
  standalone: true,
  selector: 'app-sparkline',
  template: `
    <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none"
         focusable="false" aria-hidden="true">

      <!-- Aire sous la courbe, très pâle : donne du poids à la tendance sans masquer
           le trait, qui reste l'information. -->
      @if (aire()) {
        <path [attr.d]="aire()" [attr.fill]="couleur()" fill-opacity="0.12" />
      }

      <path [attr.d]="trace()" fill="none" [attr.stroke]="couleur()"
            stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"
            vector-effect="non-scaling-stroke" />

      <!-- Dernier point marqué : indique où en est l'indicateur aujourd'hui. Sans lui,
           l'œil ne sait pas de quel côté se lit la courbe. -->
      @if (dernier()) {
        <circle [attr.cx]="dernier()!.x" [attr.cy]="dernier()!.y" r="2.5"
                [attr.fill]="couleur()" stroke="#fff" stroke-width="1.2"
                vector-effect="non-scaling-stroke" />
      }
    </svg>
  `,
  styles: [`
    :host { display: block; }
    svg { display: block; width: 100%; height: 100%; overflow: visible; }
  `],
})
export class Sparkline {

  /** Un point par jour, dans l'ordre chronologique. */
  readonly values = input<number[]>([]);

  readonly couleur = input<string>('currentColor');

  /** Repère interne du tracé. La largeur réelle vient du CSS, via `preserveAspectRatio`. */
  protected readonly W = 100;
  protected readonly H = 30;

  /** Marge haute et basse, pour que le trait ne soit pas coupé par le bord. */
  private readonly PAD = 3;

  private readonly points = computed(() => {
    const v = this.values();
    if (v.length === 0) return [];

    /*
     * Un seul point ne fait pas une ligne : on le double pour obtenir un segment
     * horizontal, visible, au lieu d'un tracé vide.
     */
    const serie = v.length === 1 ? [v[0], v[0]] : v;

    const max = Math.max(...serie);
    const haut = this.H - this.PAD;
    const bas = this.PAD;

    return serie.map((n, i) => ({
      x: (i / (serie.length - 1)) * this.W,
      // Série entièrement nulle : trait posé sur la ligne de base, et non une division
      // par zéro. Une journée sans activité doit se voir comme telle.
      y: max === 0 ? haut : haut - (n / max) * (haut - bas),
    }));
  });

  protected readonly trace = computed(() => {
    const p = this.points();
    if (p.length === 0) return '';
    return p.map((pt, i) => `${i === 0 ? 'M' : 'L'}${pt.x.toFixed(2)} ${pt.y.toFixed(2)}`).join(' ');
  });

  protected readonly aire = computed(() => {
    const p = this.points();
    if (p.length === 0) return '';
    return `${this.trace()} L${this.W} ${this.H - this.PAD} L0 ${this.H - this.PAD} Z`;
  });

  protected readonly dernier = computed(() => this.points().at(-1) ?? null);
}
