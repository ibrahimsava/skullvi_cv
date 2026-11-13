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

/** Variantes plus sombres, pour l'état survolé. */
export const CATEGORICAL_HOVER = [
  '#1739a8',
  '#0b6f68',
  '#8a4208',
  '#6128c4',
  '#990e30',
  '#116630',
  '#02547d',
  '#834f05',
] as const;

/** Couleur d'une série, en bouclant si la liste est plus longue que la palette. */
export function categorical(index: number): string {
  return CATEGORICAL[index % CATEGORICAL.length];
}

export function categoricalHover(index: number): string {
  return CATEGORICAL_HOVER[index % CATEGORICAL_HOVER.length];
}

/** Une couleur par élément d'une liste. */
export function categoricalFor(count: number): string[] {
  return Array.from({ length: count }, (_, i) => categorical(i));
}

export function categoricalHoverFor(count: number): string[] {
  return Array.from({ length: count }, (_, i) => categoricalHover(i));
}

/**
 * Teintes des deux courbes de la série temporelle.
 *
 * <p>Visites en bleu plein, visiteurs distincts en sarcelle pointillée : deux signaux
 * différents, donc deux teintes franchement distinctes. Le pointillé double l'information
 * pour qui ne distingue pas les deux couleurs.
 */
export const SERIES = {
  visits: { stroke: '#1d4ed8', fill: 'rgba(29, 78, 216, 0.12)', point: '#1d4ed8' },
  visitors: { stroke: '#0d9488', fill: 'transparent', point: '#0d9488' },
} as const;

/** Couleurs communes des axes, de la grille et des infobulles. */
export const AXIS = {
  grid: '#e4ecfb',
  border: '#dbe4f5',
  tick: '#6e7f9c',
  label: '#3c4c69',
  tooltipBg: '#0b1b3a',
} as const;
