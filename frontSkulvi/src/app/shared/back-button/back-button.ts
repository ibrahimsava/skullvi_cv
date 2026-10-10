import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

@Component({
  selector: 'app-back-button',
  standalone: true,
  template: `
    @if (visible()) {
      <button type="button" class="back" (click)="back()" aria-label="Revenir en arrière">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        <span>Retour</span>
      </button>
    }
  `,
  styles: [`
    .back {
      position: fixed;
      top: 88px;
      left: 16px;
      z-index: 15;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 9px 16px 9px 10px;
      border: 1px solid #dbe5f5;
      border-radius: 999px;
      background: rgba(255, 255, 255, .92);
      backdrop-filter: blur(8px);
      color: #0f172a;
      font: 600 14px/1 'Plus Jakarta Sans', system-ui, sans-serif;
      cursor: pointer;
      box-shadow: 0 8px 22px rgba(15, 23, 42, .1);
      transition: transform .2s, box-shadow .2s, color .2s, border-color .2s;
    }
    .back:hover {
      transform: translateX(-3px);
      color: #1d4ed8;
      border-color: #1d4ed8;
      box-shadow: 0 12px 28px rgba(29, 78, 216, .22);
    }
    .back:focus-visible { outline: 3px solid rgba(37, 99, 235, .45); outline-offset: 2px; }
    svg {
      width: 20px; height: 20px;
      fill: none; stroke: currentColor; stroke-width: 2.4;
      stroke-linecap: round; stroke-linejoin: round;
    }
    @media (max-width: 520px) {
      .back { top: 78px; padding: 10px; }
      .back span { display: none; }
    }
  `],
})
export class BackButton {
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  readonly visible = signal(false);

  constructor() {
    this.update(this.router.url);
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe((e) => this.update(e.urlAfterRedirects));
  }

  /** Masqué sur l'accueil et dans l'espace admin (qui a sa propre navigation) */
  private update(url: string): void {
    const path = url.split(/[?#]/)[0];
    this.visible.set(!(path === '/' || path === '/home' || path.startsWith('/admin')));
  }

  back(): void {
    // Angular numérote les navigations : > 1 veut dire qu'on vient d'une autre page de l'app
    const cameFromApp = (window.history.state?.navigationId ?? 1) > 1;
    if (cameFromApp) this.location.back();
    else this.router.navigateByUrl('/');   // page ouverte directement : retour à l'accueil
  }
}