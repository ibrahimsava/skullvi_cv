import { Directive, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';

/** Direction d'entrée de l'élément. */
export type RevealFrom = 'up' | 'left' | 'right' | 'zoom';

/**
 * Fait apparaître un élément quand il entre dans le champ de vision.
 *
 * <p>L'observateur se déconnecte dès le premier déclenchement : l'animation ne se rejoue
 * jamais. Une page qui s'agite chaque fois qu'on remonte devient pénible au deuxième écran,
 * et donne l'impression que le contenu n'est pas stable.
 *
 * <p>Repli volontaire vers l'état visible si {@code IntersectionObserver} manque ou si
 * l'utilisateur a demandé moins d'animations : dans les deux cas le contenu doit s'afficher,
 * jamais rester invisible.
 */
@Directive({ selector: '[appReveal]', standalone: true })
export class RevealDirective implements OnInit, OnDestroy {

  /** Décalage en millisecondes, pour échelonner les éléments d'une même rangée. */
  @Input('appReveal') delay: number | string = 0;

  @Input() revealFrom: RevealFrom = 'up';

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private io?: IntersectionObserver;

  ngOnInit(): void {
    const node = this.el.nativeElement;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (reduce || typeof IntersectionObserver === 'undefined') {
      node.classList.add('reveal', 'is-visible');
      return;
    }

    node.classList.add('reveal');
    if (this.revealFrom !== 'up') {
      node.classList.add(`from-${this.revealFrom}`.replace('from-zoom', 'zoom'));
    }
    node.style.setProperty('--d', `${Number(this.delay) || 0}ms`);

    this.io = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      node.classList.add('is-visible');
      this.io?.disconnect();
    }, {
      // Déclenche un peu avant que l'élément soit pleinement visible : l'animation est
      // déjà terminée quand le regard arrive dessus, au lieu de démarrer sous les yeux.
      threshold: 0.12,
      rootMargin: '0px 0px -60px 0px',
    });

    this.io.observe(node);
  }

  ngOnDestroy(): void {
    this.io?.disconnect();
  }
}
