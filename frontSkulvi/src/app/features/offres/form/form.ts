import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Offres } from '../offres';
import { ReactiveFormsModule, FormGroup , FormControl, Validators} from '@angular/forms';
import { CriterionType } from '../model';



@Component({
  standalone: true,
  imports: [CommonModule,ReactiveFormsModule],
  selector: 'app-form',
  styleUrl: './form.scss',
  templateUrl: './form.html',
})
export class Form extends Offres{

 

  // Niveau par défaut ou hérité
  level: string = 'Senior';


 // CORRECTION : Ajoute un nouveau groupe de critères dans le FormArray hérité
  addCriterion(): void {
    const criterionGroup = new FormGroup({
      name: new FormControl('', Validators.required),
      type: new FormControl(CriterionType.SKILL, Validators.required),
      weight: new FormControl(1, [Validators.required, Validators.min(0)]),
      threshold: new FormControl(0, [Validators.required, Validators.min(0)]),
      mandatory: new FormControl(false)
    });

    this.criteria.push(criterionGroup);
  }

 // CORRECTION : Supprime le critère à l'index sélectionné
  removeCriterion(index: number): void {
    this.criteria.removeAt(index);
  }

}
