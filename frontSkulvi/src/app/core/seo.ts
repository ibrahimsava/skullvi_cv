import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Meta, Title } from '@angular/platform-browser';
import { environment } from '../../environments/environment';

/**
 * Description d'une page pour les moteurs de recherche et les aperçus de partage.
 *
 * <p>Tout est optionnel sauf le titre et la description : ce sont les deux seules choses
 * qu'un moteur affiche réellement dans ses résultats, et les laisser vides revient à laisser
 * Google composer lui-même un extrait, souvent avec le menu de navigation.
 */
export interface PageSeo {
  /** Affiché dans l'onglet et comme lien bleu du résultat. Sans le nom du site : il est ajouté. */
  title: string;

  /** 150 à 160 caractères utiles. Au-delà, le moteur tronque. */
  description: string;

  /** Chemin canonique, commençant par « / ». Par défaut, l'adresse courante. */
  path?: string;

  /** Image d'aperçu au partage, chemin relatif à la racine du site. */
  image?: string;

  /** Vrai pour demander aux moteurs de ne pas indexer : espaces privés, page introuvable. */
  noindex?: boolean;

  /** Type Open Graph. « website » par défaut, « article » pour une offre d'emploi. */
  ogType?: 'website' | 'article';
}

const SITE_NAME = 'Skulvi';

/**
 * Image d'aperçu par défaut au partage d'un lien.
 *
 * <p>Le logo carré, faute de mieux. À remplacer par une vraie vignette de 1200 × 630 pixels :
 * c'est le format que LinkedIn, Facebook et Slack affichent en pleine largeur, alors qu'une
 * image carrée est rognée des deux côtés — souvent en plein milieu du logo.
 */
const DEFAULT_IMAGE = '/icon-512.png';

/**
 * Deux marchés, une seule langue.
 *
 * <p>France et Côte d'Ivoire se visent en français toutes les deux. Sans `hreflang`, Google
 * choisit lui-même quelle version montrer à qui, et les deux pages se concurrencent sur les
 * mêmes requêtes — exactement ce qu'on veut éviter. `fr-FR` et `fr-CI` pointant vers la même
 * adresse disent : « cette page sert les deux régions », et `x-default` sert de repli pour le
 * reste du monde.
 */
const REGIONS = ['fr-FR', 'fr-CI'] as const;

/**
 * Renseigne le titre, la description et les métadonnées de partage de chaque page.
 *
 * <p>Pourquoi un service et non des balises dans `index.html` : une application d'une seule
 * page garde le même `index.html` sur toutes ses adresses. Sans ce service, les trente pages
 * du site partagent un titre unique, et un moteur n'a aucune raison de les distinguer — donc
 * aucune raison de les faire remonter sur des requêtes différentes.
 *
 * <p><b>Limite assumée.</b> Ces balises sont posées par le navigateur, après exécution du
 * JavaScript. Google l'exécute et les voit. Les robots qui ne l'exécutent pas — l'aperçu de
 * lien de LinkedIn, WhatsApp, Facebook — ne voient que l'`index.html` d'origine. D'où les
 * valeurs de repli qui y sont écrites en dur. Le seul vrai remède est le prérendu, qui produit
 * un fichier HTML par page.
 */
@Injectable({ providedIn: 'root' })
export class Seo {

  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly doc = inject(DOCUMENT);

  /**
   * Origine du site, sans barre finale.
   *
   * <p>En production, le domaine réel : une adresse canonique doit être absolue. En
   * développement, `environment.siteUrl` est vide et on retombe sur l'origine courante —
   * écrire le domaine de production dans un canonique servi en local reviendrait à dire au
   * moteur « la vraie page est ailleurs », ce qui est la meilleure façon de se désindexer.
   */
  private readonly origin = (environment.siteUrl || this.doc.location?.origin || '')
    .replace(/\/$/, '');

  /** Pose toutes les balises d'une page. Remplace celles de la page précédente. */
  apply(page: PageSeo): void {
    const titre = page.title.includes(SITE_NAME) ? page.title : `${page.title} — ${SITE_NAME}`;
    const url = this.absolute(page.path ?? this.currentPath());
    const image = this.absolute(page.image ?? DEFAULT_IMAGE);

    this.title.setTitle(titre);

    this.set('description', page.description);
    this.set('robots', page.noindex
      ? 'noindex, nofollow'
      // « max-image-preview:large » autorise la grande vignette dans les résultats, et
      // « max-snippet:-1 » laisse le moteur afficher un extrait de la longueur qu'il juge
      // utile au lieu de le tronquer.
      : 'index, follow, max-image-preview:large, max-snippet:-1');

    // Open Graph : LinkedIn, Facebook, WhatsApp, Slack.
    this.setProperty('og:site_name', SITE_NAME);
    this.setProperty('og:type', page.ogType ?? 'website');
    this.setProperty('og:title', titre);
    this.setProperty('og:description', page.description);
    this.setProperty('og:url', url);
    this.setProperty('og:image', image);
    this.setProperty('og:locale', 'fr_FR');

    // Carte Twitter / X. « summary_large_image » affiche la vignette en grand, sinon le lien
    // partagé se réduit à une ligne de texte.
    this.set('twitter:card', 'summary_large_image');
    this.set('twitter:title', titre);
    this.set('twitter:description', page.description);
    this.set('twitter:image', image);

    this.canonical(url);
    this.alternates(url, page.noindex === true);
  }

  /**
   * Insère des données structurées JSON-LD.
   *
   * <p>C'est ce qui fait la différence entre « un lien bleu » et un résultat enrichi : le prix
   * affiché sous le lien de la page de tarifs, l'offre d'emploi reprise dans Google Jobs. Le
   * moteur ne devine rien de tout cela depuis le texte de la page.
   *
   * @param id identifiant de la balise, pour la remplacer au changement de page plutôt que
   *           d'en empiler une par navigation
   */
  jsonLd(id: string, data: unknown): void {
    const balise = `seo-jsonld-${id}`;
    this.doc.getElementById(balise)?.remove();

    if (!data) return;

    const script = this.doc.createElement('script');
    script.id = balise;
    script.type = 'application/ld+json';
    script.text = JSON.stringify(data);
    this.doc.head.appendChild(script);
  }

  /** Retire toutes les données structurées de la page quittée. */
  clearJsonLd(): void {
    this.doc.querySelectorAll('script[id^="seo-jsonld-"]').forEach((n) => n.remove());
  }

  /** Transforme un chemin en adresse absolue. Laisse passer une adresse déjà absolue. */
  absolute(pathOrUrl: string): string {
    if (/^https?:\/\//.test(pathOrUrl)) return pathOrUrl;
    return this.origin + (pathOrUrl.startsWith('/') ? pathOrUrl : `/${pathOrUrl}`);
  }

  // ---------------------------------------------------------------- interne

  private currentPath(): string {
    const l = this.doc.location;
    return l ? l.pathname : '/';
  }

  /**
   * Adresse canonique.
   *
   * <p>Dit au moteur quelle est la seule bonne adresse d'une page. Sans elle,
   * `/tarifs`, `/tarifs?utm_source=linkedin` et `/tarifs#grille` sont trois pages distinctes
   * qui se partagent le même contenu, donc la même autorité, divisée par trois.
   */
  private canonical(url: string): void {
    let lien = this.doc.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!lien) {
      lien = this.doc.createElement('link');
      lien.rel = 'canonical';
      this.doc.head.appendChild(lien);
    }
    lien.href = url;
  }

  /** Déclare que la page sert la France et la Côte d'Ivoire, en français dans les deux cas. */
  private alternates(url: string, noindex: boolean): void {
    this.doc.querySelectorAll('link[rel="alternate"][hreflang]').forEach((n) => n.remove());
    if (noindex) return;

    for (const lang of [...REGIONS, 'x-default']) {
      const lien = this.doc.createElement('link');
      lien.rel = 'alternate';
      lien.setAttribute('hreflang', lang);
      lien.href = url;
      this.doc.head.appendChild(lien);
    }
  }

  private set(name: string, content: string): void {
    this.meta.updateTag({ name, content });
  }

  private setProperty(property: string, content: string): void {
    this.meta.updateTag({ property, content }, `property='${property}'`);
  }
}
