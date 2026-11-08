import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SiteHeader } from '../../layout/site-header/site-header';
import { AuthService } from '../auth/auth';
import { User } from '../auth/model';
import { CandidateStatus, EspaceApi, MyApplication, MyProfile } from './espace-api';

type Tab = 'candidatures' | 'profil' | 'compte';

/**
 * Espace personnel du candidat.
 *
 * <p>Trois choses qu'il ne pouvait pas faire avant : suivre ses candidatures, voir ce que
 * l'analyse a réellement lu dans son CV, et retirer un dossier.
 *
 * <p>Le profil extrait est la partie la plus utile. Un candidat dont le CV est mis en page sur
 * deux colonnes ou exporté en image découvre ici que presque rien n'a été lu — et comprend
 * pourquoi ses candidatures n'aboutissent pas. C'est aussi l'exercice de son droit d'accès.
 */
@Component({
  standalone: true,
  selector: 'app-espace',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, SiteHeader],
  templateUrl: './espace.html',
  styleUrl: './espace.scss',
})
export class Espace implements OnInit {

  private readonly api = inject(EspaceApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  tab = signal<Tab>('candidatures');

  me = signal<User | null>(null);
  applications = signal<MyApplication[]>([]);
  profile = signal<MyProfile | null>(null);

  loading = signal(true);
  busyId = signal<string | null>(null);
  toast = signal<{ text: string; error: boolean } | null>(null);

  nameForm = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
  });
  savingName = signal(false);

  /** Un recruteur arrivé ici garde un lien de retour vers son espace de travail. */
  readonly isRecruiter = computed(() => this.me()?.organizationId != null);

  readonly inProgress = computed(() =>
    this.applications().filter((a) => !this.isFinal(a.status)).length);

  readonly skillCount = computed(() => this.profile()?.profile?.skills?.length ?? 0);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);

    this.api.me().subscribe({
      next: (user) => {
        this.me.set(user);
        this.nameForm.patchValue({ firstName: user.firstName, lastName: user.lastName });
      },
      error: () => this.flash('Impossible de charger votre profil.', true),
    });

    this.api.myApplications().subscribe({
      next: (apps) => {
        this.applications.set(apps);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.flash('Impossible de charger vos candidatures.', true);
      },
    });

    this.api.myProfile().subscribe({
      next: (p) => this.profile.set(p),
      error: () => this.profile.set(null),
    });
  }

  // ---------------------------------------------------------------- affichage

  /** Le dossier est-il clos, d'un côté ou de l'autre ? */
  isFinal(status: CandidateStatus): boolean {
    return status === 'SHORTLISTED' || status === 'REJECTED';
  }

  tone(status: CandidateStatus): 'ok' | 'danger' | 'accent' {
    if (status === 'SHORTLISTED') return 'ok';
    if (status === 'REJECTED') return 'danger';
    return 'accent';
  }

  /** Ce que le candidat peut concrètement faire, selon l'état de son dossier. */
  advice(status: CandidateStatus): string {
    if (status === 'SHORTLISTED') return 'Le recruteur va vous contacter.';
    if (status === 'REJECTED') return "Ce poste n'a pas abouti. D'autres offres sont ouvertes.";
    return 'Votre dossier est entre les mains du recruteur.';
  }

  years(): string {
    const y = this.profile()?.profile?.totalExperienceYears;
    if (y == null) return '—';
    return y >= 2 ? `${y} ans` : `${y} an`;
  }

  // ---------------------------------------------------------------- actions

  saveName(): void {
    if (this.savingName() || this.nameForm.invalid) {
      this.nameForm.markAllAsTouched();
      return;
    }
    this.savingName.set(true);
    const { firstName, lastName } = this.nameForm.getRawValue();

    this.api.updateName(firstName, lastName).subscribe({
      next: (user) => {
        this.savingName.set(false);
        this.me.set(user);
        // Le nom est affiché depuis la session : il faut la rafraîchir pour que l'en-tête suive.
        this.auth.refreshUser().subscribe({ error: () => {} });
        this.flash('Votre nom a été mis à jour.');
      },
      error: () => {
        this.savingName.set(false);
        this.flash('La mise à jour a échoué.', true);
      },
    });
  }

  withdraw(app: MyApplication): void {
    const message = `Retirer votre candidature pour « ${app.offerTitle} » chez ${app.organizationName} ?\n\n`
      + 'Votre CV sera définitivement effacé et le recruteur ne verra plus votre dossier.';
    if (!confirm(message)) return;

    this.busyId.set(app.id);
    this.api.withdraw(app.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.applications.update((list) => list.filter((a) => a.id !== app.id));
        this.flash('Candidature retirée et CV effacé.');
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.flash(err.status === 404
          ? 'Cette candidature a déjà été retirée.'
          : 'Le retrait a échoué. Réessayez.', true);
      },
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/');
  }

  private flash(text: string, error = false): void {
    this.toast.set({ text, error });
    setTimeout(() => this.toast.set(null), 5000);
  }
}
