/**
 * Palette catégorielle des graphiques.
 *
 * <p><b>Pourquoi ne pas tout faire en bleu.</b> Une série de barres toutes de la même teinte
 * n'apporte rien : la couleur ne distingue alors plus rien et il faut lire chaque étiquette.
 * À l'inverse, un arc-en-ciel fatigue et ne hiérarchise pas davantage.
 *
 * <p>Le compromis retenu : six teintes choisies à luminosité voisine, le bleu de la marque en
 * premier. Elles restent distinguables entre elles, y compris pour un daltonisme rouge-vert,
 * parce qu'elles diffèrent aussi par leur clarté — pas seulement par leur teinte.
 */

/** Teintes de remplissage, dans l'ordre d'attribution. */
export const CATEGORICAL = [
  '#1d4ed8',   // bleu de la marque
  '#0d9488',   // sarcelle
  '#b45309',   // ambre foncé
  '#7c3aed',   // violet
  '#be123c',   // framboise
  '#15803d',   // vert
  '#0369a1',   // bleu ardoise
  '#a16207',   // ocre
] as const;

/** Couleur d'une série, en bouclant si la liste est plus longue que la palette. */
export function categorical(index: number): string {
  return CATEGORICAL[index % CATEGORICAL.length];
}

/*
  Ce fichier contenait aussi CATEGORICAL_HOVER, categoricalHover, categoricalFor,
  categoricalHoverFor, SERIES et AXIS. Tous existaient pour configurer chart.js : teintes de
  survol, couleurs d'axes, de grille et d'infobulles.

  La page de fréquentation est passée aux petits multiples, dessinés en SVG et en CSS — le
  survol, les axes et les infobulles y sont du ressort de la feuille de style. Ces exports
  n'avaient donc plus un seul appelant. Les garder « au cas où » laisse croire qu'ils sont
  en service, et la prochaine personne les maintiendrait pour rien.
*/
