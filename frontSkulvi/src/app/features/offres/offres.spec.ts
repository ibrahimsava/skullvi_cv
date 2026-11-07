import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PublicOffer } from '../admin/admin.models';
import { Offres } from './offres';

/** Offre minimale, `closingDate` à null : le cas qui faisait planter la liste. */
function offer(overrides: Partial<PublicOffer> = {}): PublicOffer {
  return {
    id: 'o1',
    title: 'Dev Java',
    description: 'Spring Boot',
    domain: 'IT',
    level: 'Senior',
    minExperienceYears: 3,
    startDate: null,
    closingDate: null,
    status: 'OPEN',
    organizationName: 'Cabinet Leon',
    organizationSlug: 'cabinet-leon',
    ...overrides,
  };
}

/** Route sans paramètre `slug` : le job board général, et non une page carrière. */
const jobBoardRoute = { snapshot: { paramMap: { get: () => null } } };

describe('Offres', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Offres],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: jobBoardRoute },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('charge les offres depuis la route publique', () => {
    const fixture = TestBed.createComponent(Offres);
    fixture.detectChanges();

    // Doit viser /public/offers : /offers est réservé au propriétaire des offres.
    const req = http.expectOne((r) => r.url.endsWith('/public/offers'));
    req.flush([offer()]);

    expect(fixture.componentInstance.offres().length).toBe(1);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('traite une offre sans date de clôture comme ouverte', () => {
    const fixture = TestBed.createComponent(Offres);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/offers')).flush([offer()]);

    const o = fixture.componentInstance.offres()[0];
    expect(fixture.componentInstance.isOpen(o)).toBe(true);
    expect(fixture.componentInstance.deadlineLabel(o)).toBe('Candidatures ouvertes');
    expect(fixture.componentInstance.period(o)).toBe('Sans date limite');
  });

  it('considère une offre dont la date est passée comme clôturée', () => {
    const fixture = TestBed.createComponent(Offres);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/offers'))
      .flush([offer({ closingDate: '2020-01-01' })]);

    const o = fixture.componentInstance.offres()[0];
    expect(fixture.componentInstance.isOpen(o)).toBe(false);
    expect(fixture.componentInstance.deadlineLabel(o)).toBe('Offre clôturée');
  });

  it('filtre aussi sur le nom de l\'entreprise', () => {
    const fixture = TestBed.createComponent(Offres);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/offers')).flush([
      offer({ id: 'a', organizationName: 'Cabinet Leon' }),
      offer({ id: 'b', organizationName: 'Tech Recrute' }),
    ]);

    fixture.componentInstance.search.set('tech');
    expect(fixture.componentInstance.visibleOffres().map((o) => o.id)).toEqual(['b']);
  });

  it('signale une erreur de chargement sans casser la page', () => {
    const fixture = TestBed.createComponent(Offres);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/offers'))
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(fixture.componentInstance.loadError()).toBe(true);
    expect(fixture.componentInstance.offres()).toEqual([]);
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
