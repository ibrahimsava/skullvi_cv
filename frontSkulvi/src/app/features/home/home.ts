import { Component, HostListener, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { RevealDirective } from './reveal.directive';
import { CountUpDirective, SpotlightDirective, TiltDirective } from './effects.directive';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive,
    RevealDirective, CountUpDirective, SpotlightDirective, TiltDirective,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit, OnDestroy {
  progress = signal(0);

  readonly words = ['poste', 'projet', 'métier', 'profil'];
  wordIndex = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  readonly criteria = [
    { label: 'Compétences techniques', value: 88 },
    { label: 'Expérience', value: 74 },
    { label: 'Formation', value: 90 },
    { label: 'Langues', value: 65 },
  ];

  ngOnInit(): void {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!reduce) {
      this.timer = setInterval(
        () => this.wordIndex.update((i) => (i + 1) % this.words.length),
        2600
      );
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  @HostListener('window:scroll')
  onScroll(): void {
    const d = document.documentElement;
    const max = d.scrollHeight - d.clientHeight;
    this.progress.set(max > 0 ? (d.scrollTop / max) * 100 : 0);
  }
}