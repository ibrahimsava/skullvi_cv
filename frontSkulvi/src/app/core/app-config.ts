/**
 * Valeurs d'identité de l'application, définies en un seul endroit.
 *
 * <p>Ces chaînes apparaissaient recopiées dans plusieurs gabarits et composants. Une adresse
 * de contact écrite à trois endroits finit toujours par être changée à deux : on garde alors
 * un lien qui mène à une boîte abandonnée, sans que rien ne le signale.
 *
 * <p>Ce qui dépend de l'environnement — adresse de l'API, domaine public — reste dans
 * `environments/`. Ici ne vivent que des constantes de produit, identiques partout.
 */
export const APP = {
  /** Adresse de contact commercial et support. */
  contactEmail: 'contact@skulvi.fr',

  /** Téléphone affiché dans le pied de page. */
  phone: '+225 05 76 47 82 31',
  phoneHref: 'tel:+2250576478231',

  city: "Abidjan, Côte d'Ivoire",

  social: {
    linkedin: 'https://www.linkedin.com',
    facebook: 'https://www.facebook.com',
  },
} as const;

/**
 * Construit un lien `mailto:` avec un objet prérempli.
 *
 * <p>Un objet déjà rempli est plus simple à traiter à la réception qu'un message vide, et
 * évite au client d'avoir à expliquer de quel compte il parle.
 */
export function mailto(subject?: string): string {
  if (!subject) {
    return `mailto:${APP.contactEmail}`;
  }
  return `mailto:${APP.contactEmail}?subject=${encodeURIComponent(subject)}`;
}
