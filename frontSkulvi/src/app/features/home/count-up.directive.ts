import { Directive, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * Fait défiler un nombre de zéro jusqu'à sa valeur, au moment où il devient visible.
 *
 * <p>Deux précautions qui font la différence entre un effet soigné et un effet pénible :
 *
 * <ul>
 *   <li>la largeur du texte est figée avant l'animation, sinon la mise en page tremble
 *       à chaque chiffre franchi (de « 9 » à « 10 », la ligne entière se décale) ;
 *   <li>si l'utilisateur a demandé moins d'animations, la valeur finale s'affiche
 *       immédiatement.
 * </ul>
 */
@Directive({ selector: '[appCountUp]', standalone: true })
export class CountUpDirective implements OnInit, OnDestroy {

  @Input('appCountUp') target = 0;
  @Input() suffix = '';
  @Input() prefix = '';
  @Input() duration = 1500;
  /** Décalage avant le départ, pour enchaîner plusieurs compteurs. */
  @Input() countDelay = 0;

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private io?: IntersectionObserver;
  private raf = 0;
  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const node = this.el.nativeElement;
    const final = Number(this.target) || 0;
    const render = (v: number) => {
      node.textContent = `${this.prefix}${Math.round(v)}${this.suffix}`;
    };

    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (reduce || typeof IntersectionObserver === 'undefined') {
      render(final);
      return;
    }

    // Réserve la place du nombre final avant de partir de zéro.
    render(final);
    node.style.minWidth = `${node.getBoundingClientRect().width}px`;
    node.style.display = 'inline-block';
    render(0);

    this.io = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      this.io?.disconnect();

      this.timer = setTimeout(() => {
        const start = performance.now();
        const step = (now: number) => {
          const t = Math.min(1, (now - start) / this.duration);
          // Décélération cubique : rapide au départ, repos franc à l'arrivée.
          render(final * (1 - Math.pow(1 - t, 3)));
          if (t < 1) this.raf = requestAnimationFrame(step);
        };
        this.raf = requestAnimationFrame(step);
      }, this.countDelay);
    }, { threshold: 0.5 });

    this.io.observe(node);
  }

  ngOnDestroy(): void {
    this.io?.disconnect();
    cancelAnimationFrame(this.raf);
    clearTimeout(this.timer);
  }
}
