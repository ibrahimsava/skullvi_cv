import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SiteFooter } from '../../layout/site-footer/site-footer';
import { SiteHeader } from '../../layout/site-header/site-header';
import { PlanOption } from '../admin/admin.models';
import { OffresService } from '../offres/offres.service';
import { Seo } from '../../core/seo';
import { breadcrumbLd, faqLd, softwareLd } from '../../core/structured-data';

/**
 * Page de tarifs publique.
 *
 * <p>La grille vient du serveur et non d'une liste recopiée ici : les limites affichées sont
 * donc toujours celles réellement appliquées. Une page de tarifs qui promet un chiffre que le
 * code n'applique pas est un litige en préparation.
 */
@Component({
  standalone: true,
  selector: 'app-tarifs',
  imports: [RouterLink, SiteHeader, SiteFooter],
  templateUrl: './tarifs.html',
  styleUrl: './tarifs.scss',
})
export class Tarifs implements OnInit {

  private readonly offres = inject(OffresService);
  private readonly seo = inject(Seo);

  plans = signal<PlanOption[]>([]);
  loading = signal(true);
  loadError = signal(false);

  ngOnInit(): void {
    this.referencer();

    this.offres.plans().subscribe({
      next: (c) => {
        this.plans.set(c.plans);
        this.loading.set(false);

        // Les tarifs ne sont balisés qu'une fois connus : annoncer un prix au moteur avant
        // de l'avoir reçu du serveur, c'est annoncer un prix inventé.
        const site = this.seo.absolute('/').replace(/\/$/, '');
        this.seo.jsonLd('logiciel', softwareLd(site, c.plans));
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  /**
   * Balise le fil d'Ariane et la foire aux questions.
   *
   * <p>Les six questions reprennent <b>mot pour mot</b> celles affichées sur la page. Ce n'est
   * pas une précaution de style : un balisage qui annonce au moteur un contenu absent de la
   * page est une cause de sanction manuelle, et la tentation d'y glisser des questions
   * supplémentaires « pour le référencement » est exactement ce qui la déclenche.
   */
  private referencer(): void {
    const site = this.seo.absolute('/').replace(/\/$/, '');

    this.seo.jsonLd('fil', breadcrumbLd(site, [
      { nom: 'Accueil', url: '/' },
      { nom: 'Tarifs', url: '/tarifs' },
    ]));

    this.seo.jsonLd('faq', faqLd([
      {
        question: 'Le plan gratuit a-t-il une date de fin ?',
        reponse: "Non. Ce n'est pas un essai : vous pouvez mener des recrutements complets "
          + 'sans jamais payer, dans la limite de 2 offres ouvertes et 40 analyses de CV par '
          + "mois. Pas de carte bancaire à l'inscription, et aucun compte à rebours.",
      },
      {
        question: "Qu'est-ce qui compte comme une analyse ?",
        reponse: 'Une candidature déposée avec un CV. Nous en extrayons le profil, le '
          + "comparons à vos critères et calculons le score. Si l'analyse échoue de notre "
          + "côté, la relance ne vous est pas facturée.",
      },
      {
        question: 'Que se passe-t-il si je dépasse mon quota ?',
        reponse: "Sur un plan payant, rien ne s'arrête : les analyses supplémentaires sont "
          + 'facturées à l\'unité en fin de mois. Nous ne coupons pas le service d\'un client '
          + 'en pleine campagne de recrutement. Le plan gratuit, lui, attend le 1er du mois '
          + 'suivant.',
      },
      {
        question: 'Où sont stockés les CV ?',
        reponse: "Sur nos serveurs en France. L'analyse par intelligence artificielle tourne "
          + "sur notre propre infrastructure : aucun CV n'est envoyé à un service d'IA tiers.",
      },
      {
        question: "Est-ce l'IA qui décide qui est embauché ?",
        reponse: 'Non. Skulvi classe les dossiers et explique chaque point attribué. Retenir '
          + 'ou écarter un candidat reste une action humaine, faite par vous. Le score est '
          + 'calculé par des règles que vous avez définies, pas par un modèle opaque.',
      },
      {
        question: 'Puis-je arrêter quand je veux ?',
        reponse: 'Oui, sans préavis, depuis le portail de facturation. Vous redescendez alors '
          + 'sur le plan gratuit : vos offres, vos candidatures et vos CV restent en place, et '
          + "vous gardez l'accès.",
      },
    ]));
  }

  limitLabel(limit: number | null): string {
    return limit == null ? 'Illimité' : String(limit);
  }

  euros(cents: number): string {
    return (cents / 100).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  }

  /** Le plan mis en avant. Le Pro correspond au besoin d'un cabinet qui recrute en continu. */
  isFeatured(p: PlanOption): boolean {
    return p.plan === 'PRO';
  }
}
