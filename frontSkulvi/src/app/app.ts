import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Tracking } from './core/tracking';
import { SeoRouter } from './core/seo-router';

/**
 * Coquille de l'application.
 *
 * <p>Démarre la mesure d'audience et les métadonnées de référencement ici plutôt que dans
 * chaque page : un seul abonnement aux navigations, et aucune page ne peut être oubliée.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {

  private readonly tracking = inject(Tracking);
  private readonly seoRouter = inject(SeoRouter);

  ngOnInit(): void {
    this.tracking.start();
    this.seoRouter.start();
  }
}
