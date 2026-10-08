import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Offre ,CriterionType} from './model';
import { OffresService } from './offres.service';
import {
  FormArray,
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-offres',
  styleUrl: './offres.scss',
  templateUrl: './offres.html',
})
export class Offres implements OnInit {
removeCriterion(_t116: number) {
throw new Error('Method not implemented.');
}
addCriterion() {
throw new Error('Method not implemented.');
}

  private readonly offresService = inject(OffresService);
  private readonly fb = inject(FormBuilder);
  protected readonly criterionTypes = Object.values(CriterionType);

  offres = signal<Offre[]>([]);
  loadError = signal(false);
  errorMessage = signal('');

   protected offerForm = this.fb.group({

    title: [ '',[Validators.required,
       Validators.minLength(3)]],

    description: [ '',
      Validators.required
    ],

    domain: [ '',
      Validators.required
    ],

    level: [ '',
      Validators.required
    ],

    minExperienceYears: [ 0,
      [
        Validators.required,
        Validators.min(0)
      ]
    ],

    startDate: ['', Validators.required
    ],

    closingDate: ['',
      Validators.required
    ],

    criteria: this.fb.array([])
  });


  get criteria(): FormArray {
    return this.offerForm.get('criteria') as FormArray;
  }



  ngOnInit(): void {
    this.loadOffres();
  }

  loadOffres(): void {
    this.loadError.set(false);
    this.errorMessage.set('');
    console.log('Loading offers...');

    this.offresService.list().subscribe({
      next: (data) => {
        console.log('API offers response:', data);
        this.offres.set(data);
      },
      error: (err) => {
        console.error('Erreur list offers:', err);
        this.offres.set([]);
        this.loadError.set(true);
        this.errorMessage.set(
          err?.message || (err?.status ? `HTTP ${err.status}` : 'Erreur inconnue')
        );
      },
    });
  }

  // creation d'offre 
  createOffre(): void {

  if (this.offerForm.invalid) {
    this.offerForm.markAllAsTouched();
    return;
  }

  const offre = this.offerForm.getRawValue() as Offre;

  console.log('Données envoyées :', offre);

  this.offresService.create(offre).subscribe({

    next: (response) => {
      console.log('Offre créée avec succès :', response);

      // Ajouter la nouvelle offre dans la liste
      this.offres.update(current => [
        ...current,
        response
      ]);

      // Réinitialiser le formulaire
      this.offerForm.reset({
        title: '',
        description: '',
        domain: '',
        level: '',
        minExperienceYears: 0,
        startDate: '',
        closingDate: ''
      });

      // Vider les critères
      this.criteria.clear();
    },

    error: (err) => {
      console.error('Erreur lors de la création :', err);
    }

  });
}


}