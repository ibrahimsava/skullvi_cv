import { APP } from './app-config';

/**
 * Données structurées schema.org.
 *
 * <p>Ce que le moteur ne peut pas deviner en lisant la page : qu'un bloc de texte est une
 * offre d'emploi avec un lieu et un contrat, qu'un nombre est un prix mensuel, qu'une
 * entreprise a telle adresse. C'est ce qui ouvre l'accès à Google Jobs, aux prix affichés
 * sous le lien, et au panneau de connaissance.
 *
 * <p>Toutes les fonctions renvoient un objet brut, sérialisé par {@link Seo.jsonLd}. Aucune ne
 * dépend du navigateur : elles restent utilisables si un prérendu est ajouté plus tard.
 */

/** Les deux marchés visés, sous la forme attendue par schema.org. */
const ZONES_SERVIES = [
  { '@type': 'Country', name: 'France' },
  { '@type': 'Country', name: "Côte d'Ivoire" },
];

/**
 * L'éditeur du service.
 *
 * <p>Posé sur la page d'accueil uniquement : répété sur chaque page, il n'ajoute rien et
 * brouille le signal. `sameAs` relie le site aux profils sociaux, ce qui aide le moteur à
 * rattacher les mentions de la marque ailleurs sur le web.
 */
export function organizationLd(siteUrl: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    '@id': `${siteUrl}/#organisation`,
    name: 'Skulvi',
    url: siteUrl,
    logo: `${siteUrl}/icon-512.png`,
    email: APP.contactEmail,
    telephone: APP.phone,
    address: { '@type': 'PostalAddress', addressLocality: 'Abidjan', addressCountry: 'CI' },
    areaServed: ZONES_SERVIES,
    sameAs: [APP.social.linkedin, APP.social.facebook],
  };
}

/**
 * Le site lui-même.
 *
 * <p>`SearchAction` déclare que le site a une recherche interne. C'est ce qui peut faire
 * apparaître une barre de recherche directement dans les résultats Google.
 */
export function webSiteLd(siteUrl: string) {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    '@id': `${siteUrl}/#site`,
    url: siteUrl,
    name: 'Skulvi',
    inLanguage: 'fr',
    publisher: { '@id': `${siteUrl}/#organisation` },
    potentialAction: {
      '@type': 'SearchAction',
      target: { '@type': 'EntryPoint', urlTemplate: `${siteUrl}/offres?q={search_term_string}` },
      'query-input': 'required name=search_term_string',
    },
  };
}

/** Un plan de la grille tarifaire, tel que servi par l'API. */
export interface PlanPourLd {
  label: string;
  priceCents: number;
}

/**
 * Le logiciel et ses tarifs.
 *
 * <p>`SoftwareApplication` avec `offers` est ce qui permet au moteur d'afficher « À partir de
 * 0 € » sous le lien de la page de tarifs. Le plan gratuit est inclus volontairement : c'est
 * l'argument le plus fort dans un résultat de recherche.
 */
export function softwareLd(siteUrl: string, plans: PlanPourLd[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'Skulvi',
    applicationCategory: 'BusinessApplication',
    applicationSubCategory: 'Logiciel de recrutement (ATS)',
    operatingSystem: 'Navigateur web',
    url: `${siteUrl}/tarifs`,
    inLanguage: 'fr',
    provider: { '@id': `${siteUrl}/#organisation` },
    featureList: [
      'Analyse automatique des CV',
      'Notation sur grille de critères personnalisée',
      'Justification de chaque point attribué',
      'Page carrière par entreprise',
      'Suivi des candidatures',
    ],
    offers: plans.map((p) => ({
      '@type': 'Offer',
      name: p.label,
      price: (p.priceCents / 100).toFixed(2),
      priceCurrency: 'EUR',
      availability: 'https://schema.org/InStock',
      priceSpecification: {
        '@type': 'UnitPriceSpecification',
        price: (p.priceCents / 100).toFixed(2),
        priceCurrency: 'EUR',
        billingDuration: 1,
        billingIncrement: 1,
        unitText: 'MONTH',
      },
    })),
  };
}

/** Une offre d'emploi, telle que servie par `/public/offers`. */
export interface OffrePourLd {
  id: string;
  title: string;
  description?: string | null;
  domain?: string | null;
  level?: string | null;
  minExperienceYears?: number | null;
  startDate?: string | null;
  closingDate?: string | null;
  organizationName?: string | null;
}

/**
 * Une offre d'emploi.
 *
 * <p>C'est le levier de référencement le plus fort d'un site d'annonces : une offre
 * correctement balisée peut apparaître dans l'encart emploi de Google, au-dessus des
 * résultats classiques.
 *
 * <p><b>Limite à connaître.</b> L'encart emploi de Google exige un lieu de travail —
 * `jobLocation`, ou `jobLocationType: TELECOMMUTE` pour un poste à distance. Le modèle d'offre
 * de Skulvi n'a pas de champ lieu : il n'y a donc rien à déclarer, et inventer une ville
 * serait pire que de l'omettre — Google sanctionne un balisage qui ne correspond pas au
 * contenu visible. Sans ce champ, l'offre reste balisée et compréhensible par les moteurs,
 * mais n'est pas éligible à l'encart emploi. Ajouter un champ « lieu » au formulaire d'offre
 * débloquerait cette éligibilité, et c'est le seul obstacle restant.
 *
 * <p>`validThrough` vient de la date de clôture quand elle existe : Google déclasse les
 * annonces sans date de fin, et une offre pourvue qui traîne dégrade la confiance accordée à
 * tout le domaine.
 */
export function jobPostingLd(siteUrl: string, offre: OffrePourLd) {
  const ld: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'JobPosting',
    title: offre.title,
    description: offre.description || offre.title,
    inLanguage: 'fr',
    directApply: true,
    url: `${siteUrl}/postuler/${offre.id}`,
    hiringOrganization: {
      '@type': 'Organization',
      name: offre.organizationName || 'Entreprise',
    },
  };

  if (offre.domain) ld['industry'] = offre.domain;
  if (offre.closingDate) ld['validThrough'] = offre.closingDate.slice(0, 10);
  if (offre.startDate) ld['jobStartDate'] = offre.startDate.slice(0, 10);

  /*
   * L'expérience demandée, dans la forme attendue par schema.org. Zéro année est une
   * information utile — « ouvert aux débutants » — et non une absence : d'où le test sur
   * `null` plutôt que sur la valeur.
   */
  if (offre.minExperienceYears != null) {
    ld['experienceRequirements'] = {
      '@type': 'OccupationalExperienceRequirements',
      monthsOfExperience: offre.minExperienceYears * 12,
    };
  }

  return ld;
}

/**
 * Fil d'Ariane.
 *
 * <p>Remplace l'adresse brute sous le lien du résultat par un chemin lisible
 * (« Skulvi › Offres › Développeur »), ce qui augmente le taux de clic sans rien changer au
 * classement.
 */
export function breadcrumbLd(siteUrl: string, chemin: { nom: string; url: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: chemin.map((etape, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: etape.nom,
      item: etape.url.startsWith('http') ? etape.url : `${siteUrl}${etape.url}`,
    })),
  };
}

/**
 * Questions fréquentes.
 *
 * <p>Peut occuper plusieurs lignes dépliables sous le lien du résultat, ce qui en augmente
 * nettement la surface. Les réponses doivent être celles réellement visibles sur la page :
 * un balisage qui ne correspond pas au contenu est une cause de sanction manuelle.
 */
export function faqLd(questions: { question: string; reponse: string }[]) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: questions.map((q) => ({
      '@type': 'Question',
      name: q.question,
      acceptedAnswer: { '@type': 'Answer', text: q.reponse },
    })),
  };
}
