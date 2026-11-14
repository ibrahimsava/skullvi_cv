import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Sparkline } from './sparkline';

/**
 * Cas limites de la micro-courbe.
 *
 * <p>Ce sont eux qui cassent une courbe dessinée à la main : une série vide, une série
 * entièrement nulle — donc un maximum de zéro, et une division par zéro si on n'y pense pas —
 * et une série d'un seul point, qui ne fait pas une ligne. Les trois arrivent en vrai : un
 * indicateur sans activité sur la période est exactement une série de zéros.
 */
@Component({
  standalone: true,
  imports: [Sparkline],
  template: `<app-sparkline [values]="values" />`,
})
class Hote {
  values: number[] = [];
}

/**
 * Rend la courbe pour une série donnée.
 *
 * <p>Réinitialise le banc d'essai à chaque appel : le dernier test en fait deux rendus pour
 * comparer deux séries, et une configuration ne peut pas être posée après instanciation.
 */
function rendre(values: number[]): SVGElement {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ imports: [Hote] });
  const fixture = TestBed.createComponent(Hote);
  fixture.componentInstance.values = values;
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('svg');
}

/** Extrait les coordonnées « y » du tracé, dans l'ordre. */
function ordonnees(svg: SVGElement): number[] {
  const d = svg.querySelector('path:not([fill-opacity])')?.getAttribute('d') ?? '';
  return [...d.matchAll(/[ML]([\d.]+) ([\d.]+)/g)].map((m) => Number(m[2]));
}

describe('Sparkline', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('ne trace rien sur une série vide, sans planter', () => {
    const svg = rendre([]);
    expect(svg).toBeTruthy();
    expect(svg.querySelector('path:not([fill-opacity])')?.getAttribute('d')).toBe('');
    // Pas de point final non plus : il n'y a rien à marquer.
    expect(svg.querySelector('circle')).toBeNull();
  });

  it('pose un trait plat sur une série entièrement nulle', () => {
    /*
     * Le maximum vaut zéro. Sans garde, « n / max » donnerait NaN et le tracé serait
     * invalide — la courbe disparaîtrait au lieu de montrer une période sans activité.
     */
    const y = ordonnees(rendre([0, 0, 0, 0, 0]));

    expect(y.length).toBe(5);
    expect(y.every((v) => Number.isFinite(v))).toBe(true);
    expect(new Set(y).size).toBe(1);        // tous à la même hauteur
  });

  it('double un point unique pour obtenir un segment visible', () => {
    // Une seule journée de données : un « point » ne se voit pas, un segment oui.
    const y = ordonnees(rendre([7]));
    expect(y.length).toBe(2);
    expect(y[0]).toBe(y[1]);
  });

  it('place le maximum en haut et le minimum en bas', () => {
    const y = ordonnees(rendre([0, 5, 10]));

    // En SVG, « y » croît vers le bas : la plus grande valeur a donc la plus petite
    // ordonnée. Se tromper de sens retournerait toutes les courbes de la page.
    expect(y[2]).toBeLessThan(y[1]);
    expect(y[1]).toBeLessThan(y[0]);
  });

  it('marque le dernier point, pour indiquer le sens de lecture', () => {
    const svg = rendre([1, 4, 2, 9]);
    const dot = svg.querySelector('circle');

    expect(dot).toBeTruthy();
    // Dernier point : abscisse au bord droit du repère.
    expect(Number(dot!.getAttribute('cx'))).toBe(100);
  });

  it('échelle chaque série sur son propre maximum', () => {
    /*
     * Le principe des petits multiples : on compare des formes, pas des hauteurs. Deux
     * séries de grandeurs très différentes mais de même allure doivent produire le même
     * tracé, sinon la plus petite serait écrasée à plat.
     */
    expect(ordonnees(rendre([1, 2, 3]))).toEqual(ordonnees(rendre([1000, 2000, 3000])));
  });
});
