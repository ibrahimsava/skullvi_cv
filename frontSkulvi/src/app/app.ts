import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BackButton } from './shared/back-button/back-button';

@Component({
  imports: [RouterOutlet, RouterOutlet, BackButton],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontSkulvi');
}

