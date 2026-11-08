import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Tracking } from './core/tracking';

/**
 * Coquille de l'application.
 *
 * <p>Démarre la mesure d'audience ici plutôt que dans chaque page : un seul abonnement aux
 * navigations, et aucune page ne peut être oubliée.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App implements OnInit {

  private readonly tracking = inject(Tracking);

  ngOnInit(): void {
    this.tracking.start();
  }
}
