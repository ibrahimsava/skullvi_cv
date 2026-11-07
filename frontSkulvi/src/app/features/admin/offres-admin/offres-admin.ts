import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../admin-api';
import { Offer } from '../admin.models';
import { AuthService } from '../../auth/auth';

/**
 * Gestion des offres de l'organisation.
 *
 * <p>Le nombre d'offres ouvertes est plafonné par le plan : cette page est donc l'endroit où un
 * recruteur vient clôturer une offre pourvue pour libérer un emplacement. Sans elle, il n'avait
 * aucun moyen de voir combien d'offres il avait réellement ouvertes.
 */
@Component({
  standalone: true,
  selector: 'app-offres-admin',
  imports: [RouterLink, DatePipe],
  templateUrl: './offres-admin.html',
  styleUrl: './offres-admin.scss',
})
export class OffresAdmin implements OnInit {

  private readonly api = inject(AdminApi);
  private readonly auth = inject(AuthService);

  offers = signal<Offer[]>([]);
  loading = signal(true);
  busyId = signal<string | null>(null);
  toast = signal<{ text: string; error: boolean } | null>(null);

  readonly canWrite = computed(() => this.auth.canWrite());

  readonly openCount = computed(() => this.offers().filter((o) => o.status === 'OPEN').length);

  /** Les offres ouvertes d'abord : ce sont celles sur lesquelles il y a quelque chose à faire. */
  readonly sorted = computed(() =>
    [...this.offers()].sort((a, b) =>
      Number(b.status === 'OPEN') - Number(a.status === 'OPEN') ||
      b.applications - a.applications ||
      a.title.localeCompare(b.title)
    ));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getOffers().subscribe({
      next: (offers) => {
        this.offers.set(offers);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.flash('Impossible de charger vos offres.', true);
      },
    });
  }

  // ---------------------------------------------------------------- affichage

  isExpired(o: Offer): boolean {
    if (!o.closingDate) return false;
    return o.closingDate.substring(0, 10) < new Date().toLocaleDateString('en-CA');
  }

  /** Une offre peut être OPEN en base tout en ayant dépassé sa date de clôture. */
  stateLabel(o: Offer): string {
    if (o.status === 'CLOSED') return 'Clôturée';
    if (this.isExpired(o)) return 'Date dépassée';
    return 'Ouverte';
  }

  stateTone(o: Offer): 'ok' | 'warn' | '' {
    if (o.status === 'CLOSED') return '';
    return this.isExpired(o) ? 'warn' : 'ok';
  }

  mandatoryCount(o: Offer): number {
    return o.criteria.filter((c) => c.mandatory).length;
  }

  // ---------------------------------------------------------------- actions

  close(o: Offer): void {
    const message = `Clôturer « ${o.title} » ?\n\n`
      + 'Les candidats ne pourront plus postuler. Les candidatures déjà reçues restent accessibles, '
      + "et l'emplacement libéré vous permet d'ouvrir une autre offre.";
    if (!confirm(message)) return;

    this.busyId.set(o.id);
    this.api.closeOffer(o.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash('Offre clôturée.');
        this.load();
      },
      error: () => {
        this.busyId.set(null);
        this.flash("Impossible de clôturer l'offre.", true);
      },
    });
  }

  reopen(o: Offer): void {
    this.busyId.set(o.id);
    this.api.reopenOffer(o.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash('Offre réouverte.');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.flash(err.status === 402
          ? (err.error?.detail ?? 'Votre plan ne permet pas une offre ouverte de plus.')
          : "Impossible de réouvrir l'offre.", true);
      },
    });
  }

  private flash(text: string, error = false): void {
    this.toast.set({ text, error });
    setTimeout(() => this.toast.set(null), 6000);
  }
}
