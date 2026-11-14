/**
 * Configuration de développement.
 *
 * <p>URL relative, comme en production. Les appels partent donc vers le serveur de
 * développement lui-même, qui les relaie au backend via `proxy.conf.cjs` : même origine,
 * aucun CORS, et le port choisi par `ng serve` n'a plus d'importance.
 *
 * <p>C'est volontairement identique à `environment.prod.ts` sur ce point : un chemin d'appel
 * différent entre local et production est exactement ce qui fait qu'un bug ne se reproduit
 * pas chez soi.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',

  /**
   * Volontairement vide en développement.
   *
   * <p>Les adresses canoniques retombent alors sur l'origine courante. Écrire le domaine de
   * production ici ferait servir en local un canonique « la vraie page est sur
   * skulvicv.cloud » — inoffensif tant que rien n'est exposé, désastreux le jour où une
   * préproduction est accessible : le moteur désindexe la page au profit d'une autre.
   */
  siteUrl: '',
};
