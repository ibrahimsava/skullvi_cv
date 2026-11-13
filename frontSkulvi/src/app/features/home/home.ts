import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteFooter } from '../../layout/site-footer/site-footer';
import { SiteHeader } from '../../layout/site-header/site-header';
import { PlanOption, PublicOffer } from '../admin/admin.models';
import { mailto } from '../../core/app-config';
import { OffresService } from '../offres/offres.service';
import { CountUpDirective } from './count-up.directive';
import { RevealDirective } from './reveal.directive';

/** Une ligne de la grille de notation. */
interface Row {
  label: string;
  kind: string;
  mandatory: boolean;
  weight: number;
  points: number;
  /** Extrait du CV qui justifie le point. Absent si le critère n'est pas satisfait. */
  evidence?: string;
}

/** Une ligne du tableau de candidatures montré dans le héros. */
interface Candidate {
  initials: string;
  name: string;
  role: string;
  score: number;
  state: 'high' | 'review' | 'low' | 'pending';
}

interface Faq {
  q: string;
  a: string;
}

/** Nombre d'offres affichées sur l'accueil avant de renvoyer vers la liste complète. */
const OFFERS_SHOWN = 6;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, RevealDirective, CountUpDirective, SiteHeader, SiteFooter],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit, OnDestroy {

  private readonly offresService = inject(OffresService);

  // ---------------------------------------------------------------- offres

  /**
   * Offres réellement ouvertes, lues depuis l'API publique.
   *
   * <p>Les candidats doivent pouvoir voir ce qui recrute dès l'accueil, sans passer par une
   * page intermédiaire. Le bouton mène au formulaire de candidature, qui demande la création
   * d'un compte — le parcours existant.
   */
  offers = signal<PublicOffer[]>([]);
  offersLoading = signal(true);

  readonly visibleOffers = computed(() => this.offers().slice(0, OFFERS_SHOWN));
  readonly hasMoreOffers = computed(() => this.offers().length > OFFERS_SHOWN);

  /** Grille tarifaire lue depuis l'API, pour que la page n'affiche jamais un prix périmé. */
  plans = signal<PlanOption[]>([]);

  // ---------------------------------------------------------------- tableau du héros

  /**
   * Extrait d'un écran de candidatures.
   *
   * <p>Montrer l'écran réel plutôt qu'une illustration : c'est ce que le recruteur verra, et
   * ça répond à sa vraie question — « à quoi ça ressemble quand je m'en sers ? ».
   */
  readonly candidates: Candidate[] = [
    { initials: 'KB', name: 'Karim Bensaïd', role: 'Dév. back-end Java', score: 81, state: 'high' },
    { initials: 'AM', name: 'Aïcha Mensah', role: 'Dév. back-end Java', score: 76, state: 'high' },
    { initials: 'TL', name: 'Thomas Lefèvre', role: 'Dév. back-end Java', score: 64, state: 'review' },
    { initials: 'SD', name: 'Sarah Diallo', role: 'Dév. back-end Java', score: 58, state: 'review' },
    { initials: 'MO', name: 'Marc Ouédraogo', role: 'Dév. back-end Java', score: 37, state: 'low' },
    { initials: 'YN', name: 'Yasmine Ndiaye', role: 'Dév. back-end Java', score: 0, state: 'pending' },
  ];

  // ---------------------------------------------------------------- grille de notation

  /**
   * Grille de notation détaillée.
   *
   * <p>Les poids totalisent 100 et le score est la somme des points : les deux chiffres
   * tombent donc juste par construction. C'est la première chose qu'un recruteur attentif
   * vérifie, et un exemple incohérent décrédibiliserait tout le reste de la page.
   */
  readonly rows: Row[] = [
    {
      label: 'Spring Boot',
      kind: 'Compétence',
      mandatory: true,
      weight: 30,
      points: 30,
      evidence: "« Conception et mise en production d'une API REST Spring Boot 3 »",
    },
    {
      label: 'PostgreSQL',
      kind: 'Compétence',
      mandatory: true,
      weight: 20,
      points: 20,
      evidence: '« Modélisation du schéma PostgreSQL, optimisation des requêtes »',
    },
    {
      label: '5 ans d\'expérience',
      kind: 'Expérience',
      mandatory: false,
      weight: 20,
      points: 16,
      evidence: '« Développeur back-end, 2021 – 2026 » soit 4 des 5 années attendues',
    },
    {
      label: 'Kubernetes',
      kind: 'Compétence',
      mandatory: false,
      weight: 15,
      points: 0,
    },
    {
      label: 'Master informatique',
      kind: 'Formation',
      mandatory: false,
      weight: 15,
      points: 15,
      evidence: '« Master 2 Génie logiciel »',
    },
  ];

  readonly total = this.rows.reduce((sum, r) => sum + r.points, 0);

  /** Ligne dépliée dans la grille détaillée. */
  openRow = signal<string>('Spring Boot');

  /** Déclenche le remplissage des jauges quand la section entre dans le champ de vision. */
  barsFilled = signal(false);

  /** Question ouverte dans la foire aux questions. */
  openFaq = signal<number>(0);

  // ---------------------------------------------------------------- contenu

  readonly steps = [
    {
      n: '1',
      title: 'Décrivez le poste',
      text: 'Compétences attendues, années d\'expérience, formation. Chaque critère reçoit un '
        + 'poids, et vous marquez les obligatoires. C\'est votre grille, pas la nôtre.',
    },
    {
      n: '2',
      title: 'Recevez les CV',
      text: 'Votre offre apparaît ici et sur votre page carrière. Les candidats déposent leur '
        + 'CV en deux minutes, avec ou sans compte.',
    },
    {
      n: '3',
      title: 'Lisez le classement',
      text: 'Chaque candidat arrive noté, avec le détail point par point et l\'extrait de CV '
        + 'qui justifie chaque point. Vous tranchez.',
    },
  ];

  readonly pledges = [
    {
      title: 'Aucun CV ne part chez un tiers',
      text: 'Le modèle d\'analyse tourne sur notre propre infrastructure. Rien n\'est envoyé à '
        + 'OpenAI, à Google ni à personne d\'autre.',
    },
    {
      title: 'La décision reste la vôtre',
      text: 'Skulvi classe et explique. Retenir ou écarter un candidat est une action que vous '
        + 'seul effectuez.',
    },
    {
      title: 'Le score ignore la personne',
      text: 'Nom, âge, genre, photo, nationalité : rien de tout cela n\'entre dans le calcul, '
        + 'ni même dans la requête envoyée au modèle.',
    },
    {
      title: 'Le candidat voit ce qu\'on a lu',
      text: 'Il consulte le profil extrait de son CV et peut retirer sa candidature — le '
        + 'fichier est alors effacé.',
    },
  ];

  readonly faqs: Faq[] = [
    {
      q: 'Combien ça coûte pour commencer ?',
      a: 'Rien, et sans limite de durée. Le plan gratuit couvre 2 offres ouvertes, 40 analyses '
        + 'de CV par mois et 2 utilisateurs. Pas de carte bancaire, pas de compte à rebours : '
        + 'vous passez à un abonnement le jour où vous dépassez ce volume, pas avant.',
    },
    {
      q: 'Est-ce l\'intelligence artificielle qui décide qui est embauché ?',
      a: 'Non, et c\'est une distinction technique, pas une formule de prudence. Le modèle de '
        + 'langage n\'intervient qu\'à une seule étape : transformer le texte du CV en liste '
        + 'structurée. Le score est ensuite calculé en Java par une formule fixe appliquée à vos '
        + 'poids. Même CV, même offre, même note. Et la décision de retenir un candidat est une '
        + 'action que vous effectuez vous-même.',
    },
    {
      q: 'Comment évitez-vous que l\'IA invente des compétences ?',
      a: 'Pour chaque compétence annoncée, le modèle doit fournir l\'extrait du CV qui la '
        + 'justifie. Le serveur vérifie ensuite que cet extrait figure réellement dans le '
        + 'document. Une compétence sans preuve retrouvable est écartée avant le calcul.',
    },
    {
      q: 'Qu\'est-ce qui compte comme une analyse ?',
      a: 'Une candidature déposée avec un CV : extraction, comparaison à vos critères, calcul '
        + 'du score. Si l\'analyse échoue de notre côté, la relance ne vous est pas comptée.',
    },
    {
      q: 'Que se passe-t-il si je dépasse mon quota ?',
      a: 'Sur un plan payant, rien ne s\'arrête : les analyses supplémentaires sont facturées à '
        + 'l\'unité. Sur le plan gratuit, le compteur repart le 1er du mois suivant.',
    },
    {
      q: 'Où sont stockés les CV, et combien de temps ?',
      a: 'Sur nos serveurs, et l\'analyse tourne sur notre propre infrastructure. Un CV est '
        + 'supprimé dès que la candidature correspondante est supprimée — par vous, ou par le '
        + 'candidat depuis son espace.',
    },
  ];

  // ---------------------------------------------------------------- cycle de vie

  private observer?: IntersectionObserver;

  ngOnInit(): void {
    this.offresService.list().subscribe({
      next: (list) => {
        this.offers.set(list);
        this.offersLoading.set(false);
      },
      // Une API injoignable ne doit pas vider la page : la section affiche un repli.
      error: () => {
        this.offers.set([]);
        this.offersLoading.set(false);
      },
    });

    this.offresService.plans().subscribe({
      next: (c) => this.plans.set(c.plans),
      error: () => this.plans.set([]),
    });

    this.watchBreakdown();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  /**
   * Remplit les jauges quand la grille de notation devient visible.
   *
   * <p>Déclenché à l'arrivée à l'écran et non au chargement : l'animation doit se produire
   * sous les yeux du visiteur, sinon elle est terminée avant qu'il n'arrive et ne sert à rien.
   */
  private watchBreakdown(): void {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (reduce || typeof IntersectionObserver === 'undefined') {
      this.barsFilled.set(true);
      return;
    }

    // L'élément n'existe pas encore au moment du ngOnInit : on attend un tour de boucle.
    setTimeout(() => {
      const target = document.getElementById('grille-notation');
      if (!target) {
        this.barsFilled.set(true);
        return;
      }
      this.observer = new IntersectionObserver(([entry]) => {
        if (!entry.isIntersecting) return;
        this.barsFilled.set(true);
        this.observer?.disconnect();
      }, { threshold: 0.25 });
      this.observer.observe(target);
    });
  }

  // ---------------------------------------------------------------- affichage

  toggleRow(label: string): void {
    this.openRow.update((current) => (current === label ? '' : label));
  }

  toggleFaq(index: number): void {
    this.openFaq.update((current) => (current === index ? -1 : index));
  }

  /** Part du poids obtenue par un critère, pour la largeur de sa jauge. */
  ratio(row: Row): number {
    if (!this.barsFilled()) return 0;
    return row.weight === 0 ? 0 : Math.round((row.points / row.weight) * 100);
  }

  stateLabel(c: Candidate): string {
    switch (c.state) {
      case 'high': return 'Priorité élevée';
      case 'review': return 'À examiner';
      case 'low': return 'Faible';
      case 'pending': return 'En analyse';
    }
  }

  euros(cents: number): string {
    return (cents / 100).toLocaleString('fr-FR', { maximumFractionDigits: 0 });
  }

  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  isFeatured(plan: PlanOption): boolean {
    return plan.plan === 'PRO';
  }

  /** Lien de contact, construit depuis la configuration de l'application. */
  readonly contactLink = mailto('Une question sur Skulvi');

  /** Jours restants avant la clôture d'une offre, pour la pastille d'urgence. */
  closingSoon(offer: PublicOffer): boolean {
    if (!offer.closingDate) return false;
    const end = new Date(offer.closingDate.substring(0, 10)).getTime();
    const days = Math.round((end - Date.now()) / 86_400_000);
    return days >= 0 && days <= 7;
  }

  deadlineLabel(offer: PublicOffer): string {
    if (!offer.closingDate) return 'Sans date limite';
    const [y, m, d] = offer.closingDate.substring(0, 10).split('-');
    return `Jusqu'au ${d}/${m}/${y}`;
  }
}
