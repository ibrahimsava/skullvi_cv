import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicOffer } from '../admin/admin.models';
import { OffresService, PublicOrganization } from './offres.service';

/**
 * Liste publique des offres.
 *
 * <p>Deux usages avec le même composant : le job board général (`/offres`) et la page carrière
 * d'une entreprise (`/c/:slug`), qui ne diffèrent que par la source des données et l'en-tête.
 *
 * <p>Ne porte plus le formulaire de création d'offre. Il vivait ici et `Form` en héritait, ce
 * qui liait une page vue par les candidats à l'outil des recruteurs : toute retouche de la
 * liste risquait de casser la création, et réciproquement.
 */
@Component({
  standalone: true,
  imports: [RouterLink],
  selector: 'app-offres',
  styleUrl: './offres.scss',
  templateUrl: './offres.html',
})
export class Offres implements OnInit {

  private readonly offresService = inject(OffresService);
  private readonly route = inject(ActivatedRoute);

  offres = signal<PublicOffer[]>([]);
  loading = signal(true);
  loadError = signal(false);
  errorMessage = signal('');
  search = signal('');

  /** Entreprise affichée en page carrière. Null sur le job board général. */
  company = signal<PublicOrganization | null>(null);

  visibleOffres = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.offres()
      .filter((o) => !q || [o.title, o.domain, o.level, o.organizationName]
        .some((v) => v?.toLowerCase().includes(q)))
      .sort((a, b) =>
        Number(this.isOpen(b)) - Number(this.isOpen(a)) ||
        this.closingKey(a).localeCompare(this.closingKey(b))
      );
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.errorMessage.set('');

    const slug = this.route.snapshot.paramMap.get('slug');

    if (slug) {
      this.offresService.organization(slug).subscribe({
        next: (org) => this.company.set(org),
        error: () => this.company.set(null),
      });
    }

    const source$ = slug ? this.offresService.byOrganization(slug) : this.offresService.list();

    source$.subscribe({
      next: (data) => {
        this.offres.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.offres.set([]);
        this.loading.set(false);
        this.loadError.set(true);
        this.errorMessage.set(err?.status === 404
          ? 'entreprise inconnue'
          : err?.status ? `erreur ${err.status}` : 'serveur injoignable');
      },
    });
  }

  // ---------- Affichage des cartes ----------

  /**
   * Le serveur ne renvoie publiquement que des offres ouvertes et non expirées. Le contrôle est
   * refait ici parce qu'une page laissée ouverte dans un onglet peut franchir une date de clôture.
   */
  isOpen(o: PublicOffer): boolean {
    return o.status === 'OPEN' && this.daysLeft(o) >= 0;
  }

  isUrgent(o: PublicOffer): boolean {
    const d = this.daysLeft(o);
    return this.isOpen(o) && d !== Infinity && d <= 7;
  }

  deadlineLabel(o: PublicOffer): string {
    if (!this.isOpen(o)) return 'Offre clôturée';
    const d = this.daysLeft(o);
    if (d === Infinity) return 'Candidatures ouvertes';
    if (d === 0) return 'Dernier jour pour postuler';
    if (d === 1) return 'Clôture demain';
    if (d <= 14) return `Clôture dans ${d} jours`;
    return `Clôture le ${this.fmt(o.closingDate)}`;
  }

  period(o: PublicOffer): string {
    if (!o.closingDate) {
      return o.startDate ? `À partir du ${this.fmt(o.startDate)}` : 'Sans date limite';
    }
    return o.startDate
      ? `Du ${this.fmt(o.startDate)} au ${this.fmt(o.closingDate)}`
      : `Jusqu'au ${this.fmt(o.closingDate)}`;
  }

  private fmt(date: string | null): string {
    if (!date) return '—';
    const [y, m, d] = date.substring(0, 10).split('-');
    return `${d}/${m}/${y}`;
  }

  /**
   * Nombre de jours avant clôture, {@code Infinity} si l'offre n'a pas de date limite.
   *
   * <p>Le cas sans date n'était pas traité : `closingDate.substring` plantait sur un null et
   * faisait échouer l'affichage de toute la liste.
   */
  private daysLeft(o: PublicOffer): number {
    if (!o.closingDate) return Infinity;
    const [y, m, d] = o.closingDate.substring(0, 10).split('-').map(Number);
    const end = new Date(y, m - 1, d);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    return Math.round((end.getTime() - today.getTime()) / 86_400_000);
  }

  /** Clé de tri : les offres sans date de clôture passent en dernier. */
  private closingKey(o: PublicOffer): string {
    return o.closingDate ?? '9999-12-31';
  }
}
