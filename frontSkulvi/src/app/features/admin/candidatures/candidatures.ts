import { Component, OnInit, computed, inject, signal,  HostListener } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { AdminApi } from '../admin-api';
import { ApplicationSummary, Offer, RankedCandidate } from '../admin.models';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

type Filter = 'tous' | 'classes' | 'echec';
interface Row extends ApplicationSummary { rank: number | null; }

@Component({
  standalone: true,
  selector: 'app-candidatures',
  imports: [DatePipe],
  templateUrl: './candidatures.html',
  styleUrl: './candidatures.scss',
})
export class Candidatures implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly route = inject(ActivatedRoute);


  private readonly sanitizer = inject(DomSanitizer);
cvViewer = signal<{ url: string; safeUrl: SafeResourceUrl; name: string } | null>(null);

  offers = signal<Offer[]>([]);
  rows = signal<Row[]>([]);
  selectedOfferId = signal('');
  filter = signal<Filter>('tous');
  loading = signal(false);
  busyId = signal<string | null>(null);
  toast = signal<{ text: string; error: boolean } | null>(null);

  failedCount = computed(() => this.rows().filter((r) => this.isFailed(r)).length);
  rankedCount = computed(() => this.rows().filter((r) => r.rank !== null).length);

  visibleRows = computed(() => {
    const f = this.filter();
    return this.rows().filter((r) =>
      f === 'echec' ? this.isFailed(r) : f === 'classes' ? r.rank !== null : true
    );
  });

  ngOnInit(): void {
    // Permet d'arriver directement sur l'onglet « En échec » depuis le tableau de bord
    if (this.route.snapshot.queryParamMap.get('filtre') === 'echec') this.filter.set('echec');

    this.api.getOffers().subscribe((offers) => {
      this.offers.set(offers);
      if (offers.length) this.selectOffer(offers[0].id);
    });
  }

  selectOffer(id: string): void {
    this.selectedOfferId.set(id);
    this.load(id);
  }

  reload(): void {
    if (this.selectedOfferId()) this.load(this.selectedOfferId());
  }

  private load(offerId: string): void {
    this.loading.set(true);

    forkJoin({
      apps: this.api.getApplications(offerId),
      ranking: this.api.getRanking(offerId).pipe(catchError(() => of([] as RankedCandidate[]))),
    }).subscribe({
      next: ({ apps, ranking }) => {
        const rankOf = new Map(ranking.map((r) => [r.applicationId, r.rank]));
        const rows: Row[] = apps.map((a) => ({ ...a, rank: rankOf.get(a.id) ?? null }));

        // Ordre de mérite d'abord (rang), puis les autres dossiers du plus récent au plus ancien
        rows.sort((a, b) =>
          (a.rank ?? Number.MAX_SAFE_INTEGER) - (b.rank ?? Number.MAX_SAFE_INTEGER) ||
          b.submittedAt.localeCompare(a.submittedAt)
        );

        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.flash('Impossible de charger les candidatures.', true);
      },
    });
  }

  // ---------- Affichage ----------

  isFailed(r: ApplicationSummary): boolean {
    return !!r.failureReason || r.status === 'FAILED';
  }

  hasScore(r: ApplicationSummary): boolean {
    return r.score !== null && r.score !== undefined && !this.isFailed(r);
  }

  stateOf(r: Row): 'failed' | 'pending' {
    return this.isFailed(r) ? 'failed' : 'pending';
  }

  stateLabel(r: Row): string {
    return this.isFailed(r) ? 'Analyse échouée' : 'En cours d\'analyse';
  }

  initialOf(r: ApplicationSummary): string {
    return (r.candidateName || r.email || '?').charAt(0).toUpperCase();
  }

  pct(score: number): number { return Math.min(100, Math.max(0, score)); }
  tone(score: number): 'high' | 'mid' | 'low' { return score >= 70 ? 'high' : score >= 40 ? 'mid' : 'low'; }

  priorityLabel(p: string | null): string {
    const labels: Record<string, string> = { HIGH: 'Élevée', MEDIUM: 'Moyenne', LOW: 'Faible' };
    return p ? (labels[p] ?? p) : '—';
  }

  // ---------- Actions ----------

 viewCv(r: Row): void {
  this.api.downloadCv(r.id).subscribe({
    next: (blob) => {
      this.closeCv();   // libère un éventuel CV déjà ouvert
      const url = URL.createObjectURL(blob);
      this.cvViewer.set({
        url,
        safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(url),
        name: r.candidateName,
      });
    },
    error: (err) => this.flash(`Impossible d'ouvrir ce CV (code ${err.status}).`, true),
  });
}

@HostListener('document:keydown.escape')
closeCv(): void {
  const v = this.cvViewer();
  if (v) URL.revokeObjectURL(v.url);
  this.cvViewer.set(null);
}

  download(r: Row): void {
    this.api.downloadCv(r.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `CV-${this.safe(r.candidateName)}${this.extOf(blob.type)}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.flash('Impossible de télécharger ce CV.', true),
    });
  }

  reanalyze(r: Row): void {
    this.busyId.set(r.id);
    this.api.analyze(r.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash('Analyse relancée. Utilisez « Actualiser » dans quelques secondes.');
        this.reload();
      },
      error: () => {
        this.busyId.set(null);
        this.flash("Impossible de relancer l'analyse.", true);
      },
    });
  }

  remove(r: Row): void {
    if (!confirm(`Supprimer la candidature de ${r.candidateName} ? Le CV sera effacé définitivement.`)) return;

    this.busyId.set(r.id);
    this.api.deleteApplication(r.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.flash('Candidature supprimée.');
        this.reload();   // recalcule les rangs
      },
      error: (err) => {
        this.busyId.set(null);
        this.flash(
          err.status === 404 || err.status === 405
            ? "Suppression indisponible : l'endpoint DELETE n'existe pas encore côté serveur."
            : 'Suppression impossible.',
          true
        );
      },
    });
  }

  closeOffer(): void {
    if (!confirm('Clôturer cette offre ? Les candidats ne pourront plus postuler.')) return;
    this.api.closeOffer(this.selectedOfferId()).subscribe({
      next: () => this.flash('Offre clôturée.'),
      error: () => this.flash("Impossible de clôturer l'offre.", true),
    });
  }

  // ---------- Utilitaires ----------

  private flash(text: string, error = false): void {
    this.toast.set({ text, error });
    setTimeout(() => this.toast.set(null), 6000);
  }

  private safe(name: string): string {
    return (name || 'candidat').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9]+/g, '-');
  }

  private extOf(type: string): string {
    if (type.includes('pdf')) return '.pdf';
    if (type.includes('wordprocessingml')) return '.docx';
    if (type.includes('msword')) return '.doc';
    return '';
  }
}