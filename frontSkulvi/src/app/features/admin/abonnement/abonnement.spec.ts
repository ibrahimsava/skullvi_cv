import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PlanCatalog, PlanOption, UsageSnapshot } from '../admin.models';
import { PlanCode } from '../../auth/model';
import { Abonnement } from './abonnement';

/**
 * Le plan affiché comme « le vôtre » suit-il le plan réel ?
 *
 * <p>Le bug que ces tests verrouillent : la grille des tarifs était lue une seule fois à
 * l'ouverture de la page, et le surlignage venait du drapeau `current` posé par le serveur
 * sur chacun de ses tarifs. Rafraîchir la consommation ne rafraîchissait donc pas la grille,
 * et « Votre plan » restait collé sur Gratuit — après un paiement réussi comme après un
 * changement fait à la main depuis l'administration.
 */

function usage(plan: PlanCode, paid: boolean): UsageSnapshot {
  return {
    plan,
    planLabel: plan,
    status: 'ACTIVE',
    paid,
    currentPeriodEnd: null,
    cancelAtPeriodEnd: false,
    period: '2026-11',
    analysesUsed: 3,
    analysesLimit: 40,
    overageCount: 0,
    overageCents: 0,
    activeOffers: 1,
    activeOffersLimit: 2,
    seats: 1,
    seatsLimit: 2,
    writable: true,
  };
}

function option(plan: PlanCode, current: boolean): PlanOption {
  return {
    plan,
    label: plan,
    priceCents: plan === 'FREE' ? 0 : 1400,
    maxActiveOffers: 2,
    maxAnalysesPerMonth: 40,
    maxSeats: 2,
    allowsOverage: false,
    overageCents: 10,
    current,
  };
}

/** Grille telle que le serveur la sert quand l'organisation est encore au gratuit. */
function catalogue(): PlanCatalog {
  return { plans: [option('FREE', true), option('PRO', false), option('BUSINESS', false)] };
}

function setup(paiement?: string) {
  TestBed.configureTestingModule({
    imports: [Abonnement],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            queryParamMap: { get: (k: string) => (k === 'paiement' ? (paiement ?? null) : null) },
          },
        },
      },
    ],
  });

  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(Abonnement);
  fixture.detectChanges();

  return { fixture, http, component: fixture.componentInstance };
}

/** Répond aux trois appels du démarrage, l'organisation étant au plan gratuit. */
function repondreAuDemarrage(http: HttpTestingController, plan: PlanCode = 'FREE', paid = false) {
  http.expectOne((r) => r.url.endsWith('/org/usage')).flush(usage(plan, paid));
  http.expectOne((r) => r.url.endsWith('/org/plans')).flush(catalogue());
  http.expectOne((r) => r.url.endsWith('/org/billing/status')).flush({ online: true });
}

describe('Abonnement', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('surligne le plan gratuit au départ', () => {
    const { component, http } = setup();
    repondreAuDemarrage(http);

    expect(component.estCourant(option('FREE', true))).toBe(true);
    expect(component.estCourant(option('PRO', false))).toBe(false);
  });

  it("suit le plan réel après un rechargement, sans relire la grille des tarifs", () => {
    const { component, http } = setup();
    repondreAuDemarrage(http);

    // Le plan a changé côté serveur — paiement encaissé, ou changement manuel depuis
    // l'administration de la plateforme.
    component.reload();
    http.expectOne((r) => r.url.endsWith('/org/usage')).flush(usage('PRO', true));

    /*
     * Le cœur du test. La grille renvoyée au démarrage dit encore « current: true » sur
     * FREE, et elle n'est pas relue. C'est exactement la situation où l'ancienne version
     * laissait « Votre plan » sur Gratuit.
     */
    expect(component.estCourant(option('PRO', false))).toBe(true);
    expect(component.estCourant(option('FREE', true))).toBe(false);
  });

  it('ne lance aucune attente si on ne revient pas du paiement', () => {
    const { component, http } = setup();
    repondreAuDemarrage(http);

    expect(component.activationEnAttente()).toBe(false);
  });

  it("resynchronise l'identité stockée, qui porte le plan affiché dans l'en-tête", () => {
    const { http } = setup();
    repondreAuDemarrage(http);

    /*
     * L'identité est conservée dans le navigateur, et le plan en fait partie. Elle peut
     * donc être périmée si l'abonnement a changé pendant que l'utilisateur était sur une
     * autre page : sans cette relecture, le bandeau du haut continuerait d'annoncer
     * « Gratuit » alors que la page, elle, serait à jour.
     */
    http.expectOne((r) => r.url.endsWith('/me'));
  });

  it("affiche l'attente d'activation au retour du paiement", () => {
    // Stripe redirige dès que le paiement est accepté, donc presque toujours avant que le
    // webhook signé n'ait été traité : au retour, le plan est encore Gratuit.
    const { component, http } = setup('ok');
    repondreAuDemarrage(http);

    expect(component.outcome()).toBe('ok');
    expect(component.activationEnAttente()).toBe(true);
    expect(component.activationTropLongue()).toBe(false);
  });

  it('ne lance pas d\'attente quand le paiement a été abandonné', () => {
    const { component, http } = setup('annule');
    repondreAuDemarrage(http);

    expect(component.outcome()).toBe('annule');
    expect(component.activationEnAttente()).toBe(false);
  });
});
