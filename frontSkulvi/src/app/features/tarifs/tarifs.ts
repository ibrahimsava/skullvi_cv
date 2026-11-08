import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteFooter } from '../../layout/site-footer/site-footer';
import { SiteHeader } from '../../layout/site-header/site-header';
import { PlanOption } from '../admin/admin.models';
import { OffresService } from '../offres/offres.service';

/**
 * Page de tarifs publique.
 *
 * <p>La grille vient du serveur et non d'une liste recopiée ici : les limites affichées sont
 * donc toujours celles réellement appliquées. Une page de tarifs qui promet un chiffre que le
 * code n'applique pas est un litige en préparation.
 */
@Component({
  standalone: true,
  selector: 'app-tarifs',
  imports: [RouterLink, SiteHeader, SiteFooter],
  templateUrl: './tarifs.html',
  styleUrl: './tarifs.scss',
})
export class Tarifs implements OnInit {

  private readonly offres = inject(OffresService);

  plans = signal<PlanOption[]>([]);
  loading = signal(true);
  loadError = signal(false);

  ngOnInit(): void {
    this.offres.plans().subscribe({
      next: (c) => {
        this.plans.set(c.plans);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  euros(cents: number): string {
    return (cents / 100).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  }

  /** Le plan mis en avant. Le Pro correspond au besoin d'un cabinet qui recrute en continu. */
  isFeatured(p: PlanOption): boolean {
    return p.plan === 'PRO';
  }
}
