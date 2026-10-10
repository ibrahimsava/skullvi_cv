import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../auth/auth';
import { Offer } from '../admin/admin.models';
import { ApplicationApi, MeResponse } from './application-api';

type Mode = 'loggedIn' | 'create' | 'login';

const URL_RE = /^https?:\/\/\S+$/i;
const MAX_FILE_SIZE = 5 * 1024 * 1024;   // 5 Mo

@Component({
  standalone: true,
  selector: 'app-postuler',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './postuler.html',
  styleUrl: './postuler.scss',
})
export class Postuler implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApplicationApi);

  private readonly offerId = this.route.snapshot.paramMap.get('offerId') ?? '';

  offer = signal<Offer | null>(null);
  loadError = signal(false);
  mode = signal<Mode>('create');
  me = signal<MeResponse | null>(null);
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

  closed = computed(() => {
    const o = this.offer();
    if (!o) return false;
    const today = new Date().toLocaleDateString('en-CA');   // YYYY-MM-DD
    return o.status === 'CLOSED' || o.closingDate < today;
  });

  get submitLabel(): string {
    if (this.loading()) return 'Envoi en cours…';
    return this.mode() === 'create' ? 'Créer mon compte et postuler' : 'Envoyer ma candidature';
  }

  ngOnInit(): void {
    const logged = this.auth.isLoggedIn();
    // Un jeton expiré envoyé à /auth/* ferait échouer la requête : on le retire
    if (!logged) localStorage.removeItem('accessToken');

    this.setMode(logged ? 'loggedIn' : 'create');

    this.api.getOffer(this.offerId).subscribe({
      next: (o) => this.offer.set(o),
      error: () => this.loadError.set(true),
    });

    if (logged) this.api.getMe().subscribe((me) => this.me.set(me));
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
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
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
      input.value = '';
      this.form.controls.cv.setValue(null);
      this.fileName.set('');
      return;
    }

    this.form.controls.cv.setValue(file);
    this.form.controls.cv.markAsTouched();
    this.fileName.set(file?.name ?? '');
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
        ? this.auth
            .createUser({ firstName: v.firstName, lastName: v.lastName, email: v.email, password: v.password })
            .pipe(switchMap(() => this.login(v.email, v.password)))
        : mode === 'login'
          ? this.login(v.email, v.password)
          : of(null);

    ready$.pipe(
      // Le compte existe et la session est ouverte : un nouvel essai ne recréera pas le compte
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
        console.error('Erreur candidature :', err);
        this.loading.set(false);
        this.handleError(err, stage, mode);
      },
    });
  }

  private login(email: string, password: string) {
    return this.auth.login(email, password).pipe(
      tap((res) => {
        try {
          localStorage.setItem('accessToken', res.accessToken);
          if (res.user) localStorage.setItem('user', JSON.stringify(res.user));
        } catch {}
      })
    );
  }

  private buildFormData(
    me: MeResponse,
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

  private handleError(err: HttpErrorResponse, stage: 'account' | 'application', mode: Mode): void {
    if (stage === 'account') {
      if (mode === 'create' && err.status === 409) {
        this.setMode('login');
        this.error.set('Un compte existe déjà avec cette adresse e-mail. Connectez-vous avec votre mot de passe.');
      } else if (err.status === 401) {
        this.error.set('E-mail ou mot de passe incorrect.');
      } else {
        this.error.set('Connexion impossible pour le moment. Réessayez dans un instant.');
      }
      return;
    }

    if (err.status === 409) this.error.set('Vous avez déjà postulé à cette offre.');
    else if (err.status === 413) this.error.set('Le fichier est trop volumineux.');
    else this.error.set("Votre candidature n'a pas pu être envoyée. Vérifiez le formulaire et réessayez.");
  }
}