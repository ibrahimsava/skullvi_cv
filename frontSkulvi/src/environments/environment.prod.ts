export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',

  /**
   * Domaine public du site, sans barre finale.
   *
   * <p>Sert aux adresses canoniques, aux aperçus de partage et aux données structurées, qui
   * doivent toutes être absolues. À tenir à jour si le domaine change : un canonique pointant
   * vers un domaine mort désindexe le site entier.
   */
  siteUrl: 'https://skulvicv.cloud',
};
