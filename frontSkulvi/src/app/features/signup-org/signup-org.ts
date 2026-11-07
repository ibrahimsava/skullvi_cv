import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth';
import { OrgSignupRequest } from '../auth/model';

/**
 * Création d'un espace recruteur.
 *
 * <p>Point d'entrée du produit payant : un formulaire, aucune carte bancaire, et l'essai démarre.
 * Demander un moyen de paiement avant que le client ait vu un seul CV analysé ferait perdre la
 * majorité des inscriptions.
 */
@Component({
  standalone: true,
  selector: 'app-signup-org',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './signup-org.html',
  styleUrl: './signup-org.scss',
})
export class SignupOrg {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  loading = signal(false);
  error = signal('');

  form = this.fb.nonNullable.group({
    organizationName: ['', [Validators.required, Validators.maxLength(80)]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  invalid(name: string): boolean {
    const c = this.form.get(name);
    return !!c && c.invalid && c.touched;
  }

  submit(): void {
    if (this.loading()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.auth.signupOrganization(this.form.getRawValue() as OrgSignupRequest).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/admin/dashboard');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(err.status === 409
          ? 'Un compte existe déjà avec cette adresse e-mail. Connectez-vous ou utilisez une autre adresse.'
          : "La création de l'espace a échoué. Réessayez dans un instant.");
      },
    });
  }
}
