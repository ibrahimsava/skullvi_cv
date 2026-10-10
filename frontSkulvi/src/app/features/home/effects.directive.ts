import { Directive, ElementRef, HostListener, Input, OnDestroy, OnInit, inject } from '@angular/core';

/** Compte de 0 jusqu'à la valeur quand l'élément devient visible */
@Directive({ selector: '[appCountUp]', standalone: true })
export class CountUpDirective implements OnInit, OnDestroy {
  @Input('appCountUp') target = 0;
  @Input() suffix = '';
  @Input() duration = 1600;

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private io?: IntersectionObserver;
  private raf = 0;

  ngOnInit(): void {
    const node = this.el.nativeElement;
    const final = Number(this.target) || 0;
    const show = (v: number) => (node.textContent = `${Math.round(v)}${this.suffix}`);

    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (reduce || typeof IntersectionObserver === 'undefined') {
      show(final);
      return;
    }
    show(0);
    this.io = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      this.io?.disconnect();
      const start = performance.now();
      const tick = (now: number) => {
        const t = Math.min(1, (now - start) / this.duration);
        show(final * (1 - Math.pow(1 - t, 3)));   // décélération douce
        if (t < 1) this.raf = requestAnimationFrame(tick);
      };
      this.raf = requestAnimationFrame(tick);
    }, { threshold: 0.4 });
    this.io.observe(node);
  }

  ngOnDestroy(): void {
    this.io?.disconnect();
    cancelAnimationFrame(this.raf);
  }
}

/** Inclinaison 3D qui suit la souris (ordinateur uniquement) */
@Directive({ selector: '[appTilt]', standalone: true })
export class TiltDirective {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);

  @HostListener('pointermove', ['$event'])
  move(e: PointerEvent): void {
    if (e.pointerType !== 'mouse') return;
    const r = this.el.nativeElement.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5;
    const y = (e.clientY - r.top) / r.height - 0.5;
    this.el.nativeElement.style.transform =
      `perspective(900px) rotateY(${x * 9}deg) rotateX(${-y * 9}deg)`;
  }

  @HostListener('pointerleave')
  leave(): void {
    this.el.nativeElement.style.transform = '';
  }
}

/** Halo lumineux qui suit le curseur à l'intérieur d'une carte */
@Directive({ selector: '[appSpotlight]', standalone: true })
export class SpotlightDirective {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);

  @HostListener('pointermove', ['$event'])
  move(e: PointerEvent): void {
    const r = this.el.nativeElement.getBoundingClientRect();
    this.el.nativeElement.style.setProperty('--mx', `${e.clientX - r.left}px`);
    this.el.nativeElement.style.setProperty('--my', `${e.clientY - r.top}px`);
  }
}