import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../admin-api';
import { InviteRequest, Member, MemberList } from '../admin.models';
import { AuthService } from '../../auth/auth';
import { OrgRole } from '../../auth/model';

@Component({
  standalone: true,
  selector: 'app-equipe',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './equipe.html',
  styleUrl: './equipe.scss',
})
export class Equipe implements OnInit {

  private readonly api = inject(AdminApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly roles: { value: OrgRole; label: string; help: string }[] = [
    { value: 'OWNER', label: 'Propriétaire', help: "Gère l'abonnement et l'équipe, en plus du recrutement." },
    { value: 'RECRUITER', label: 'Recruteur', help: 'Crée des offres et traite les candidatures.' },
    { value: 'VIEWER', label: 'Lecteur', help: 'Consulte les classements, sans rien modifier.' },
  ];

  team = signal<MemberList | null>(null);
  loading = signal(true);
  showForm = signal(false);
  saving = signal(false);
  busyId = signal<string | null>(null);
  error = signal('');
  toast = signal<{ text: string; error: boolean } | null>(null);

  readonly canManage = computed(() => this.auth.canManageOrganization());

  /** Reste-t-il un siège disponible sur le plan ? */
  readonly seatsLeft = computed(() => {
    const t = this.team();
    if (!t || t.seatsLimit == null) return null;
    return Math.max(0, t.seatsLimit - t.seats);
  });

  readonly seatsFull = computed(() => this.seatsLeft() === 0);

  inviteForm = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: ['RECRUITER' as OrgRole, Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getMembers().subscribe({
      next: (t) => {
        this.team.set(t);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.flash("Impossible de charger l'équipe.", true);
      },
    });
  }

  // ---------------------------------------------------------------- affichage

  roleLabel(role: OrgRole): string {
    return this.roles.find((r) => r.value === role)?.label ?? role;
  }

  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  initial(m: Member): string {
    return (m.firstName || m.email || '?').charAt(0).toUpperCase();
  }

  invalid(name: string): boolean {
    const c = this.inviteForm.get(name);
    return !!c && c.invalid && c.touched;
  }

  // ---------------------------------------------------------------- actions

  toggleForm(): void {
    this.showForm.update((v) => !v);
    this.error.set('');
  }

  invite(): void {
    if (this.saving()) return;
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set('');

    this.api.invite(this.inviteForm.getRawValue() as InviteRequest).subscribe({
      next: () => {
        this.saving.set(false);
        this.showForm.set(false);
        this.inviteForm.reset({ role: 'RECRUITER' });
        this.flash('Membre ajouté. Transmettez-lui son mot de passe initial.');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.messageFor(err));
      },
    });
  }

  changeRole(m: Member, role: string): void {
    if (role === m.role) return;

    this.busyId.set(m.membershipId);
    this.api.changeRole(m.membershipId, role as OrgRole).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash(`${m.firstName} est maintenant ${this.roleLabel(role as OrgRole).toLowerCase()}.`);
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.flash(this.messageFor(err), true);
        this.load();   // remet la liste déroulante sur la valeur réelle
      },
    });
  }

  remove(m: Member): void {
    const message = `Retirer ${m.firstName} ${m.lastName} de l'équipe ?\n\n`
      + "Son compte n'est pas supprimé : il perd seulement l'accès à votre espace.";
    if (!confirm(message)) return;

    this.busyId.set(m.membershipId);
    this.api.removeMember(m.membershipId).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash('Membre retiré de l\'équipe.');
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.busyId.set(null);
        this.flash(this.messageFor(err), true);
      },
    });
  }

  /**
   * Les deux codes qui comptent ici : 402 pour la limite de sièges du plan, 409 pour le dernier
   * propriétaire. Dans les deux cas le serveur envoie une phrase exploitable, autant la montrer
   * plutôt que de la remplacer par un message générique.
   */
  private messageFor(err: HttpErrorResponse): string {
    if (err.status === 402 || err.status === 409) {
      return err.error?.detail ?? 'Action impossible avec votre plan actuel.';
    }
    if (err.status === 403) return "Seul le propriétaire du compte gère l'équipe.";
    return "L'opération a échoué. Réessayez.";
  }

  private flash(text: string, error = false): void {
    this.toast.set({ text, error });
    setTimeout(() => this.toast.set(null), 6000);
  }
}
