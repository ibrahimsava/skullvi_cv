import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { APP } from '../../core/app-config';

/** Pied de page des pages publiques. */
@Component({
  standalone: true,
  selector: 'app-site-footer',
  imports: [RouterLink],
  templateUrl: './site-footer.html',
  styleUrl: './site-footer.scss',
})
export class SiteFooter {
  readonly year = new Date().getFullYear();

  /** Coordonnées et réseaux, lus depuis la configuration plutôt que recopiés ici. */
  readonly app = APP;
}
