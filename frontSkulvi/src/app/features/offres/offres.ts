import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Offre } from './model';
import { OffresService } from './offres.service';
import { NgForm } from '@angular/forms';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';


@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-offres',
  styleUrl: './offres.scss',
  templateUrl: './offres.html',
})
export class Offres implements  OnInit{

  private readonly offresService = inject(OffresService);

  offres: Offre[] = [];
  loadError = false;
  errorMessage = '';

  ngOnInit(): void {
    this.loadOffres();
  }


  loadOffres(): void {
    this.loadError = false;
    this.errorMessage = '';
    console.log('Loading offers...');
    this.offresService.list().subscribe({
      next: (data) => {
        console.log('API offers response:', data);
        this.offres = data;
      },
      error: (err) => {
        console.error('Erreur list offers:', err);
        this.offres = [];
        this.loadError = true;
        this.errorMessage = err?.message || (err?.status ? `HTTP ${err.status}` : 'Erreur inconnue');
      }
    });
  }     






}
