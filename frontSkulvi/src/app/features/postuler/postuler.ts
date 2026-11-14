import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';
import { SiteHeader } from '../../layout/site-header/site-header';
import { AuthService } from '../auth/auth';
import { User } from '../auth/model';
import { PublicOffer } from '../admin/admin.models';
import { ApplicationApi } from './application-api';
import { Seo } from '../../core/seo';
import { breadcrumbLd, jobPostingLd } from '../../core/structured-data';

type Mode = 'loggedIn' | 'create' | 'login';

const URL_RE = /^https?:\/\/\S+$/i;
const MAX_FILE_SIZE = 5 * 1024 * 1024;   // 5 Mo, aligné sur la limite du serveur

@Component({
  standalone: true,
  selector: 'app-postuler',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, SiteHeader],
  templateUrl: './postuler.html',
  styleUrl: './postuler.scss',
})
export class Postuler implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApplicationApi);
  private readonly seo = inject(Seo);

  private readonly offerId = this.route.snapshot.paramMap.get('offerId') ?? '';

  offer = signal<PublicOffer | null>(null);
  loadError = signal(false);
  mode = signal<Mode>('create');
  me = signal<User | null>(null);
  loading = signal(false);
  error = signal('');
  done = signal(false);
  fileName = signal('');

  form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true }),
    lastName: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true }),
    linkedinUrl: new FormControl('', { nonNullable: true, validators: [Validators.pattern(URL_RE)] }),
    githubUrl: new FormControl('', { nonNullable: true, validators: [Validators.pattern(URL_RE)] }),
    portfolioUrl: new FormControl('', { nonNullable: true, validators: [Validators.pattern(URL_RE)] }),
    cv: new FormControl<File | null>(null, Validators.required),
  });

  /**
   * L'offre refuse-t-elle les candidatures ?
   *
   * <p>Le cas « pas de date de clôture » était traité comme une date dépassée : `undefined`
   * comparé à une chaîne renvoie faux, mais le serveur accepte ces offres indéfiniment. Elles
   * sont donc ouvertes, et non fermées.
   */
  closed = computed(() => {
    const o = this.offer();
    if (!o) return false;
    if (o.status !== 'OPEN') return true;
    if (!o.closingDate) return false;
    return o.closingDate.substring(0, 10) < new Date().toLocaleDateString('en-CA');
  });

  get submitLabel(): string {
    if (this.loading()) return 'Envoi en cours…';
    return this.mode() === 'create' ? 'Créer mon compte et postuler' : 'Envoyer ma candidature';
  }

  ngOnInit(): void {
    const logged = this.auth.isLoggedIn();
    // Un jeton expiré resté dans le navigateur ferait échouer les appels : on nettoie.
    if (!logged) this.auth.logout();

    this.setMode(logged ? 'loggedIn' : 'create');

    this.api.getOffer(this.offerId).subscribe({
      next: (o) => {
        this.offer.set(o);
        this.referencer(o);
      },
      error: () => this.loadError.set(true),
    });

    if (logged) {
      this.api.getMe().subscribe({
        next: (me) => this.me.set(me),
        error: () => this.setMode('login'),
      });
    }
  }

  setMode(m: Mode): void {
    this.mode.set(m);
    this.error.set('');

    const c = this.form.controls;
    const create = m === 'create';
    const login = m === 'login';

    c.firstName.setValidators(create ? [Validators.required] : []);
    c.lastName.setValidators(create ? [Validators.required] : []);
    c.email.setValidators(create || login ? [Validators.required, Validators.email] : []);
    c.password.setValidators(
      create ? [Validators.required, Validators.minLength(8), Validators.maxLength(72)]
      : login ? [Validators.required]
      : []
    );
    Object.values(c).forEach((ctrl) => ctrl.updateValueAndValidity());
  }

  switchAccount(): void {
    this.auth.logout();
    this.me.set(null);
    this.setMode('login');
  }

  invalid(name: string): boolean {
    const c = this.form.get(name);
    return !!c && c.invalid && c.touched;
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.error.set('');

    if (file && file.size > MAX_FILE_SIZE) {
      this.error.set('Ce fichier dépasse 5 Mo. Choisissez un CV plus léger.');
      this.clearFile(input);
      return;
    }

    /*
     * Le serveur n'accepte que du PDF : il vérifie la signature du fichier, pas son extension.
     * Autant le dire tout de suite plutôt que de laisser l'envoi échouer après l'upload.
     */
    if (file && !file.name.toLowerCase().endsWith('.pdf')) {
      this.error.set('Seuls les fichiers PDF sont acceptés. Exportez votre CV en PDF.');
      this.clearFile(input);
      return;
    }

    this.form.controls.cv.setValue(file);
    this.form.controls.cv.markAsTouched();
    this.fileName.set(file?.name ?? '');
  }

  private clearFile(input: HTMLInputElement): void {
    input.value = '';
    this.form.controls.cv.setValue(null);
    this.fileName.set('');
  }

  submit(): void {
    if (this.loading()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const mode = this.mode();
    let stage: 'account' | 'application' = 'account';

    this.loading.set(true);
    this.error.set('');

    const ready$: Observable<unknown> =
      mode === 'create'
        ? this.auth.createUser({
            firstName: v.firstName, lastName: v.lastName, email: v.email, password: v.password,
          })
        : mode === 'login'
          ? this.auth.login(v.email, v.password)
          : of(null);

    ready$.pipe(
      // Le compte existe et la session est ouverte : un nouvel essai ne recréera pas le compte.
      tap(() => { if (mode !== 'loggedIn') this.setMode('loggedIn'); }),
      switchMap(() => this.api.getMe()),
      tap((me) => { this.me.set(me); stage = 'application'; }),
      switchMap((me) => this.api.apply(this.offerId, this.buildFormData(me, v))),
    ).subscribe({
      next: () => {
        this.loading.set(false);
        this.done.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.messageFor(err, stage, mode));
      },
    });
  }

  private buildFormData(
    me: User,
    v: { phone: string; linkedinUrl: string; githubUrl: string; portfolioUrl: string; cv: File | null }
  ): FormData {
    const fd = new FormData();
    fd.append('firstName', me.firstName);
    fd.append('lastName', me.lastName);
    fd.append('email', me.email);

    const optional: Record<string, string> = {
      phone: v.phone, linkedinUrl: v.linkedinUrl, githubUrl: v.githubUrl, portfolioUrl: v.portfolioUrl,
    };
    for (const [key, value] of Object.entries(optional)) {
      if (value.trim()) fd.append(key, value.trim());
    }

    fd.append('cv', v.cv as File);
    return fd;
  }

  /**
   * Message d'erreur utile au candidat.
   *
   * <p>Deux nouveaux cas depuis le passage en abonnement, et ils ne sont pas de son fait :
   * 402 quand l'entreprise a épuisé son quota d'analyses, 429 quand trop de candidatures sont
   * parties de la même connexion. Les présenter comme un problème de formulaire l'enverrait
   * corriger un CV qui n'a rien à se reprocher.
   */
  private messageFor(err: HttpErrorResponse, stage: 'account' | 'application', mode: Mode): string {
    if (stage === 'account') {
      if (mode === 'create' && err.status === 409) {
        this.setMode('login');
        return 'Un compte existe déjà avec cette adresse e-mail. Connectez-vous avec votre mot de passe.';
      }
      if (err.status === 401) return 'E-mail ou mot de passe incorrect.';
      return 'Connexion impossible pour le moment. Réessayez dans un instant.';
    }

    switch (err.status) {
      case 409:
        return 'Vous avez déjà postulé à cette offre.';
      case 413:
        return 'Le fichier est trop volumineux (5 Mo maximum).';
      case 402:
        return "Cette entreprise ne peut plus recevoir de candidatures pour le moment. "
          + 'Réessayez dans quelques jours, ou contactez-la directement.';
      case 429:
        return 'Trop de candidatures envoyées depuis cette connexion. Patientez quelques minutes.';
      case 400:
        return 'Votre CV doit être un fichier PDF valide. Exportez-le à nouveau depuis votre traitement de texte.';
      default:
        return "Votre candidature n'a pas pu être envoyée. Vérifiez le formulaire et réessayez.";
    }
  }

  // ---------------------------------------------------------------- référencement

  /**
   * Décrit l'offre aux moteurs de recherche, une fois qu'elle est chargée.
   *
   * <p>Le titre réel compte ici plus qu'ailleurs : un candidat cherche « développeur Java
   * Abidjan », pas « postuler à cette offre ». Une page d'annonce qui garde un titre générique
   * ne peut remonter sur aucune recherche de poste, et c'est pourtant le trafic le plus
   * qualifié que le site puisse recevoir.
   */
  private referencer(offre: PublicOffer): void {
    const chezQui = offre.organizationName ? ` chez ${offre.organizationName}` : '';

    this.seo.apply({
      title: `${offre.title}${chezQui}`,
      description: this.extrait(offre),
      path: `/postuler/${offre.id}`,
      ogType: 'article',
    });

    const site = this.seo.absolute('/').replace(/\/$/, '');
    this.seo.jsonLd('offre', jobPostingLd(site, offre));
    this.seo.jsonLd('fil', breadcrumbLd(site, [
      { nom: 'Accueil', url: '/' },
      { nom: "Offres d'emploi", url: '/offres' },
      { nom: offre.title, url: `/postuler/${offre.id}` },
    ]));
  }

  /**
   * Description courte pour les résultats de recherche.
   *
   * <p>Coupée au dernier mot entier avant 155 caractères : au-delà, le moteur tronque
   * lui-même, et le fait volontiers au milieu d'un mot.
   */
  private extrait(offre: PublicOffer): string {
    const brut = (offre.description || '').replace(/\s+/g, ' ').trim();
    const texte = brut || `${offre.title} — candidature en ligne, analyse de votre CV.`;

    if (texte.length <= 155) return texte;
    const coupe = texte.slice(0, 155);
    return `${coupe.slice(0, coupe.lastIndexOf(' '))}…`;
  }
}
