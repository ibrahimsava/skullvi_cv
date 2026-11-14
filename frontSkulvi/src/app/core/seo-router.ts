import { Injectable, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { PageSeo, Seo } from './seo';
import { organizationLd, webSiteLd } from './structured-data';

/**
 * Applique les métadonnées de référencement à chaque navigation.
 *
 * <p>Branché une seule fois au démarrage, comme la mesure d'audience, et pour la même raison :
 * poser les balises page par page garantit qu'une page finira par être oubliée — et une page
 * sans titre propre est une page que le moteur n'a aucune raison de distinguer des autres.
 *
 * <p>Les valeurs viennent de `data.seo` dans les routes. Une page dont le contenu n'est connu
 * qu'après un appel réseau — une offre d'emploi, une page carrière — complète ensuite en
 * appelant {@link Seo.apply} elle-même : ce qui est posé ici est un repli correct, affiché le
 * temps du chargement.
 */
@Injectable({ providedIn: 'root' })
export class SeoRouter {

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly seo = inject(Seo);

  start(): void {
    this.appliquer();

    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.appliquer());
  }

  private appliquer(): void {
    const page = this.seoDeLaRouteActive();

    /*
     * Les données structurées de la page précédente sont retirées à chaque navigation. Sans
     * cela, une offre d'emploi resterait balisée sur la page de tarifs : le moteur verrait un
     * balisage qui ne correspond à rien de visible, ce qui est une cause de sanction.
     */
    this.seo.clearJsonLd();

    this.seo.apply(page);

    // L'éditeur et le site ne sont déclarés que sur l'accueil : répétés partout, ils
    // n'ajoutent rien et diluent le signal.
    if (this.router.url.split(/[?#]/)[0] === '/') {
      const origine = this.seo.absolute('/').replace(/\/$/, '');
      this.seo.jsonLd('organisation', organizationLd(origine));
      this.seo.jsonLd('site', webSiteLd(origine));
    }
  }

  /** Descend jusqu'à la route affichée : c'est l'enfant le plus profond qui décrit la page. */
  private seoDeLaRouteActive(): PageSeo {
    let route = this.route.snapshot;
    let page: PageSeo | undefined;

    while (route) {
      page = (route.data?.['seo'] as PageSeo | undefined) ?? page;
      if (!route.firstChild) break;
      route = route.firstChild;
    }

    return page ?? REPLI;
  }
}

/**
 * Repli si une route oublie sa description.
 *
 * <p>Marqué `noindex` à dessein : mieux vaut une page absente de l'index qu'une page indexée
 * avec un titre générique, qui ferait concurrence à l'accueil sur ses propres mots clés.
 */
const REPLI: PageSeo = {
  title: 'Skulvi',
  description: 'Tri et analyse de CV pour les recruteurs.',
  noindex: true,
};
