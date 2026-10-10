import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Criterion, CriterionType, Offre, OfferStatus } from './model';
import { OffresService } from './offres.service';

const MAX_CHIPS = 6;

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  selector: 'app-offres',
  styleUrl: './offres.scss',
  templateUrl: './offres.html',
})
export class Offres implements OnInit {

  private readonly offresService = inject(OffresService);
  private readonly fb = inject(FormBuilder);
  protected readonly criterionTypes = Object.values(CriterionType);

  offres = signal<Offre[]>([]);
  loading = signal(true);
  loadError = signal(false);
  errorMessage = signal('');
  search = signal('');

  /** Offres affichées au public : sans les brouillons, filtrées, ouvertes d'abord */
  visibleOffres = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.offres()
      .filter((o) => o.status !== OfferStatus.DRAFT)
      .filter((o) => !q || [o.title, o.domain, o.level].some((v) => v?.toLowerCase().includes(q)))
      .sort((a, b) =>
        Number(this.isOpen(b)) - Number(this.isOpen(a)) ||
        a.closingDate.localeCompare(b.closingDate)
      );
  });

  protected offerForm = this.fb.group({
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

  ngOnInit(): void {
    this.loadOffres();
  }

  loadOffres(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.errorMessage.set('');

    this.offresService.list().subscribe({
      next: (data) => {
        this.offres.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Erreur list offers:', err);
        this.offres.set([]);
        this.loading.set(false);
        this.loadError.set(true);
        this.errorMessage.set(
          err?.status ? `erreur ${err.status}` : 'serveur injoignable'
        );
      },
    });
  }

  // ---------- Affichage des cartes ----------

  isOpen(o: Offre): boolean {
    return o.status === OfferStatus.OPEN && this.daysLeft(o) >= 0;
  }

  isUrgent(o: Offre): boolean {
    return this.isOpen(o) && this.daysLeft(o) <= 7;
  }

  deadlineLabel(o: Offre): string {
    const d = this.daysLeft(o);
    if (!this.isOpen(o)) return 'Offre clôturée';
    if (d === 0) return "Dernier jour pour postuler";
    if (d === 1) return 'Clôture demain';
    if (d <= 14) return `Clôture dans ${d} jours`;
    return `Clôture le ${this.fmt(o.closingDate)}`;
  }

  period(o: Offre): string {
    return o.startDate
      ? `Du ${this.fmt(o.startDate)} au ${this.fmt(o.closingDate)}`
      : `Jusqu'au ${this.fmt(o.closingDate)}`;
  }

  shownCriteria(o: Offre): Criterion[] {
    return [...o.criteria]
      .sort((a, b) => Number(b.mandatory) - Number(a.mandatory) || b.weight - a.weight)
      .slice(0, MAX_CHIPS);
  }

  hiddenCount(o: Offre): number {
    return Math.max(0, o.criteria.length - MAX_CHIPS);
  }

  private fmt(date: string): string {
    const [y, m, d] = date.substring(0, 10).split('-');
    return `${d}/${m}/${y}`;
  }

  private daysLeft(o: Offre): number {
    const [y, m, d] = o.closingDate.substring(0, 10).split('-').map(Number);
    const end = new Date(y, m - 1, d);
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    return Math.round((end.getTime() - today.getTime()) / 86_400_000);
  }

  // ---------- Création d'offre (utilisé par le formulaire admin) ----------

  createOffre(): void {
    if (this.offerForm.invalid) {
      this.offerForm.markAllAsTouched();
      return;
    }

    const offre = this.offerForm.getRawValue() as unknown as Offre;

    this.offresService.create(offre).subscribe({
      next: (response) => {
        this.offres.update((current) => [...current, response]);

        this.offerForm.reset({
          title: '',
          description: '',
          domain: '',
          level: '',
          minExperienceYears: 0,
          startDate: '',
          closingDate: '',
        });
        this.criteria.clear();
      },
      error: (err) => {
        console.error('Erreur lors de la création :', err);
      },
    });
  }
}