import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Form } from './form';

describe('Form', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Form],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /*
   * Le composant ne charge plus la liste des offres au démarrage : il héritait pour cela de la
   * page publique. `http.verify()` échouerait si une requête partait encore.
   */
  it('ne lance aucune requête au démarrage', () => {
    const fixture = TestBed.createComponent(Form);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('ajoute et retire des critères', () => {
    const c = TestBed.createComponent(Form).componentInstance;
    expect(c.criteria.length).toBe(0);

    c.addCriterion();
    c.addCriterion();
    expect(c.criteria.length).toBe(2);

    c.removeCriterion(0);
    expect(c.criteria.length).toBe(1);
  });

  it('cumule le poids des critères', () => {
    const c = TestBed.createComponent(Form).componentInstance;
    c.addCriterion();
    c.addCriterion();
    c.criteria.at(0).get('weight')!.setValue(40);
    c.criteria.at(1).get('weight')!.setValue(25);

    expect(c.totalWeight).toBe(65);
  });

  it('refuse d\'envoyer un formulaire incomplet', () => {
    const c = TestBed.createComponent(Form).componentInstance;
    c.createOffre();
    // Aucune requête attendue : http.verify() dans afterEach le confirme.
    expect(c.offerForm.touched).toBe(true);
  });

  /*
   * Le 402 est la limite d'offres du plan. Il doit remonter le message du serveur : un message
   * générique laisserait le recruteur relancer sans comprendre qu'il doit clôturer une offre.
   */
  it('affiche le motif du serveur quand le plan plafonne les offres', () => {
    const fixture = TestBed.createComponent(Form);
    const c = fixture.componentInstance;

    c.offerForm.patchValue({
      title: 'Dev Java',
      description: 'Spring',
      domain: 'IT',
      level: 'Senior',
      minExperienceYears: 3,
      startDate: '2026-01-01',
      closingDate: '2026-06-01',
    });
    c.addCriterion();
    c.criteria.at(0).get('name')!.setValue('Spring Boot');

    c.createOffre();

    http.expectOne((r) => r.url.endsWith('/offers')).flush(
      { detail: 'Votre plan Essai autorise 1 offre(s) ouverte(s).' },
      { status: 402, statusText: 'Payment Required' }
    );

    expect(c.error()).toBe('Votre plan Essai autorise 1 offre(s) ouverte(s).');
    expect(c.saving()).toBe(false);
  });
});
