import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { Offres } from '../offres';
import { CriterionType } from '../model';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-form',
  styleUrl: './form.scss',
  templateUrl: './form.html',
})
export class Form extends Offres {

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
}