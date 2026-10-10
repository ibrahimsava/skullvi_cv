import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth';

@Component({
  standalone: true,
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  collapsed = signal(this.readCollapsed());
  email = this.auth.getUserEmail();

  get initial(): string {
    return (this.email ?? 'A').charAt(0).toUpperCase();
  }

  toggle(): void {
    this.collapsed.update((v) => !v);
    try { localStorage.setItem('adminSidebarCollapsed',
       String(this.collapsed())); } catch {}
  }

  logout(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    this.router.navigateByUrl('/login');
  }

  private readCollapsed(): boolean {
    try { return localStorage.getItem('adminSidebarCollapsed') === 'true'; } 
    catch { return false; }
  }
}