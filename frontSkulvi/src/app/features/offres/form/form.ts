import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import {
  FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { AdminApi } from '../../admin/admin-api';
import { CriterionType } from '../model';

/**
 * Création d'une offre avec sa grille de notation.
 *
 * <p>Composant autonome. Il héritait auparavant de la page publique `Offres` pour en récupérer
 * le formulaire, ce qui mélangeait l'outil du recruteur et la vitrine des candidats — et
 * chargeait au passage la liste complète des offres, inutilisée ici.
 */
@Component({
  standalone: true,
  imports: [ReactiveFormsModule],
  selector: 'app-form',
  styleUrl: './form.scss',
  templateUrl: './form.html',
})
export class Form {

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);

  protected readonly criterionTypes = Object.values(CriterionType);

  saving = signal(false);
  error = signal('');

  offerForm = this.fb.group({
    title: ['', [Validators.required, Validators.minLength(3)]],
    description: ['', Validators.required],
    domain: ['', Validators.required],
    level: ['', Validators.required],
    minExperienceYears: [0, [Validators.required, Validators.min(0)]],
    startDate: ['', Validators.required],
    closingDate: ['', Validators.required],
    criteria: this.fb.array([], Validators.required),
  });

  get criteria(): FormArray {
    return this.offerForm.get('criteria') as FormArray;
  }

  addCriterion(): void {
    this.criteria.push(new FormGroup({
      name: new FormControl('', Validators.required),
      type: new FormControl(CriterionType.SKILL, Validators.required),
      weight: new FormControl(10, [Validators.required, Validators.min(1), Validators.max(100)]),
      threshold: new FormControl(0, [Validators.required, Validators.min(0)]),
      mandatory: new FormControl(false),
    }));
  }

  removeCriterion(index: number): void {
    this.criteria.removeAt(index);
  }

  invalid(name: string): boolean {
    const c = this.offerForm.get(name);
    return !!c && c.invalid && c.touched;
  }

  get totalWeight(): number {
    return this.criteria.controls.reduce(
      (sum, c) => sum + (Number(c.get('weight')?.value) || 0), 0
    );
  }

  createOffre(): void {
    if (this.saving()) return;
    if (this.offerForm.invalid) {
      this.offerForm.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set('');

    this.api.createOffer(this.offerForm.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigateByUrl('/admin/offres');
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(messageFor(err));
      },
    });
  }
}

/**
 * Traduit l'erreur du serveur en phrase utile.
 *
 * <p>Le 402 compte vraiment : c'est la limite d'offres du plan. Un « une erreur est survenue »
 * générique laisserait le recruteur relancer indéfiniment sans comprendre qu'il doit clôturer
 * une offre ou changer d'abonnement.
 */
function messageFor(err: HttpErrorResponse): string {
  if (err.status === 402) {
    return err.error?.detail ?? 'Votre plan ne permet pas d\'ouvrir une offre supplémentaire.';
  }
  if (err.status === 403) {
    return 'Votre rôle ne permet pas de créer une offre.';
  }
  return "L'offre n'a pas pu être créée. Vérifiez le formulaire et réessayez.";
}
