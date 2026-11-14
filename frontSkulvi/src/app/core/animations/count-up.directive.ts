import {
  Directive, ElementRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject,
} from '@angular/core';

/**
 * Fait défiler un nombre de zéro jusqu'à sa valeur.
 *
 * <p>Deux déclencheurs, et c'est la différence entre une page vitrine et un tableau de bord :
 *
 * <ul>
 *   <li><b>À l'entrée dans le champ de vision</b>, quand la valeur est connue dès le départ —
 *       le cas de la page d'accueil, dont les chiffres sont écrits dans le gabarit ;
 *   <li><b>À l'arrivée de la valeur</b>, quand elle vient d'un appel réseau — le cas des
 *       statistiques. La version précédente ne lisait la cible qu'une fois, au montage : un
 *       indicateur encore à zéro à cet instant animait de zéro vers zéro, puis affichait
 *       brutalement son vrai chiffre à la réception. C'est exactement l'effet « tout apparaît
 *       d'un coup » qu'on cherche à éviter.
 * </ul>
 *
 * <p>Trois précautions qui font la différence entre un effet soigné et un effet pénible :
 *
 * <ul>
 *   <li>la largeur du texte est figée avant l'animation, sinon la mise en page tremble à
 *       chaque chiffre franchi — de « 9 » à « 10 », toute la ligne se décale ;
 *   <li>une animation en cours est annulée avant d'en lancer une autre, sinon deux boucles
 *       écrivent dans le même élément et le nombre se met à clignoter entre deux valeurs ;
 *   <li>si l'utilisateur a demandé moins d'animations, la valeur finale s'affiche tout de
 *       suite. Un effet décoratif ne doit jamais retarder une information.
 * </ul>
 */
@Directive({ selector: '[appCountUp]', standalone: true })
export class CountUpDirective implements OnInit, OnChanges, OnDestroy {

  @Input('appCountUp') target = 0;
  @Input() suffix = '';
  @Input() prefix = '';
  @Input() duration = 1100;
  /** Décalage avant le départ, pour échelonner plusieurs compteurs d'une même rangée. */
  @Input() countDelay = 0;

  /** Chiffres après la virgule. Utile pour un taux, inutile pour un décompte. */
  @Input() decimals = 0;

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private io?: IntersectionObserver;
  private raf = 0;
  private timer?: ReturnType<typeof setTimeout>;

  /** Valeur affichée, point de départ de la prochaine animation. */
  private courant = 0;

  /** Vrai dès que l'élément a été vu : les mises à jour suivantes s'animent sans attendre. */
  private vu = false;

  private get reduit(): boolean {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
  }

  ngOnInit(): void {
    const final = this.valeur();

    if (this.reduit || typeof IntersectionObserver === 'undefined') {
      this.vu = true;
      this.afficher(final);
      return;
    }

    this.reserverLaPlace(final);
    this.afficher(0);

    this.io = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      this.io?.disconnect();
      this.vu = true;
      this.animer(this.valeur(), this.countDelay);
    }, { threshold: 0.5 });

    this.io.observe(this.el.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Le premier passage est traité par ngOnInit, qui dispose déjà de la valeur.
    if (changes['target']?.firstChange !== false) return;

    const final = this.valeur();

    if (this.reduit) {
      this.afficher(final);
      return;
    }

    this.reserverLaPlace(final);

    // Pas encore visible : la nouvelle cible sera prise au passage de l'observateur, qui
    // relit `valeur()` au moment de partir. Rien à faire ici.
    if (!this.vu) return;

    // Déjà visible, et la valeur change : on anime depuis ce qui est affiché, sans délai.
    // Repartir de zéro à chaque changement de période ferait clignoter tout le tableau.
    this.animer(final, 0);
  }

  ngOnDestroy(): void {
    this.arreter();
    this.io?.disconnect();
  }

  // ---------------------------------------------------------------- interne

  private valeur(): number {
    const n = Number(this.target);
    return Number.isFinite(n) ? n : 0;
  }

  private animer(final: number, delai: number): void {
    this.arreter();

    const depart = this.courant;
    if (depart === final) {
      this.afficher(final);
      return;
    }

    this.timer = setTimeout(() => {
      const t0 = performance.now();
      const step = (now: number) => {
        const t = Math.min(1, (now - t0) / Math.max(1, this.duration));
        // Décélération cubique : franc au départ, repos net à l'arrivée. Une interpolation
        // linéaire donne l'impression d'un compteur mécanique, pas d'une transition.
        const progres = 1 - Math.pow(1 - t, 3);
        this.afficher(depart + (final - depart) * progres);
        if (t < 1) this.raf = requestAnimationFrame(step);
      };
      this.raf = requestAnimationFrame(step);
    }, delai);
  }

  private arreter(): void {
    cancelAnimationFrame(this.raf);
    clearTimeout(this.timer);
  }

  private afficher(valeur: number): void {
    this.courant = valeur;
    const arrondi = this.decimals > 0
      ? valeur.toFixed(this.decimals)
      : String(Math.round(valeur));
    this.el.nativeElement.textContent = `${this.prefix}${arrondi}${this.suffix}`;
  }

  /**
   * Réserve la largeur du nombre final avant d'animer.
   *
   * <p>Mesurée en écrivant brièvement la valeur d'arrivée dans l'élément. Sans cela, il
   * s'élargit au fil des chiffres et pousse tout ce qui le suit : sur une rangée de quatre
   * indicateurs, les trois autres se déplacent pendant toute l'animation.
   *
   * <p>L'écriture est faite <b>directement</b> sur le nœud, sans passer par {@link #afficher},
   * qui tiendrait à jour {@code courant} : la mesure écraserait alors le point de départ de
   * l'animation, et le nombre sauterait à sa valeur finale avant de s'animer.
   */
  private reserverLaPlace(final: number): void {
    const node = this.el.nativeElement;
    const avant = node.textContent ?? '';
    const arrondi = this.decimals > 0 ? final.toFixed(this.decimals) : String(Math.round(final));

    node.textContent = `${this.prefix}${arrondi}${this.suffix}`;
    node.style.display = 'inline-block';

    // `max` et non une affectation sèche : à un changement de période, le nouveau nombre peut
    // être plus court que l'ancien, et la colonne se mettrait à rétrécir puis s'élargir.
    const mesure = node.getBoundingClientRect().width;
    const reserve = parseFloat(node.style.minWidth) || 0;
    node.style.minWidth = `${Math.max(mesure, reserve)}px`;

    node.textContent = avant;
  }
}
