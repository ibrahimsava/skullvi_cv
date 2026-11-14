import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Page introuvable.
 *
 * <p>Remplace l'ancienne redirection vers l'accueil. Trois raisons :
 *
 * <ul>
 *   <li><b>Référencement.</b> Renvoyer le contenu de l'accueil sur une adresse inexistante est
 *       ce que Google appelle une « soft 404 » : il voit une page valide là où il n'y a rien,
 *       l'indexe, puis finit par douter de la qualité de tout le domaine. La route est
 *       maintenant marquée `noindex`.
 *   <li><b>Lisibilité.</b> Un visiteur qui suit un lien périmé se retrouvait sur l'accueil
 *       sans comprendre pourquoi, en croyant avoir mal cliqué.
 *   <li><b>Diagnostic.</b> Une faute dans un lien interne passait inaperçue, puisqu'elle
 *       affichait une page d'apparence normale.
 * </ul>
 */
@Component({
  standalone: true,
  selector: 'app-not-found',
  imports: [RouterLink],
  templateUrl: './not-found.html',
  styleUrl: './not-found.scss',
})
export class NotFound {}
