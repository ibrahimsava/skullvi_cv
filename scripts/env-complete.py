#!/usr/bin/env python3
"""Ajoute a .env les variables manquantes, avec des valeurs sures pour le developpement.

Toutes ont une valeur par defaut dans application.yaml : l'application demarre sans elles.
Les ecrire explicitement sert a deux choses — voir d'un coup d'oeil ce qui est reglable, et
savoir quoi changer au moment de passer en production.

Usage :
    python scripts/env-complete.py            # ajoute ce qui manque
    python scripts/env-complete.py --dry-run  # montre ce qui serait ajoute

Aucune valeur existante n'est ecrasee, et aucun secret n'est affiche.
"""
from __future__ import annotations

import pathlib
import sys

ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'

# (variable, valeur de developpement, commentaire)
MANQUANTES = [
    (None, None, "---------------------------------------------------------------- Adresses"),
    ('PUBLIC_URL', 'http://localhost:4200',
     "Adresse publique du site. Sert aux retours de paiement : en production, le vrai\n"
     "#  domaine, sinon le client est renvoyé sur sa propre machine après avoir payé."),
    ('CORS_ORIGIN', 'http://localhost:4200,http://localhost:*',
     "Origines autorisées par le navigateur, séparées par des virgules. Une entrée\n"
     "#  contenant une étoile est traitée comme un motif.\n"
     "#  En production : uniquement le vrai domaine.\n"
     "#  En local, ce réglage ne sert à rien : le mandataire du serveur de développement\n"
     "#  (frontSkulvi/proxy.conf.cjs) met le front et l'API sur la même origine."),

    (None, None, "---------------------------------------------------------------- Sécurité"),
    ('JWT_EXPIRATION_MINUTES', '480',
     "Durée de validité du jeton. 480 minutes = une journée de travail : un recruteur\n"
     "#  ne doit pas être déconnecté en pleine session de tri."),
    ('SWAGGER_ENABLED', 'true',
     "Documentation interactive de l'API.\n"
     "#  À passer à false en production : elle décrit toute la surface d'attaque."),

    (None, None, "---------------------------------------------------------------- Paiement"),
    ('STRIPE_SUCCESS_PATH', '/admin/abonnement?paiement=ok',
     "Pages de retour après un passage en caisse. Chemins relatifs à PUBLIC_URL."),
    ('STRIPE_CANCEL_PATH', '/admin/abonnement?paiement=annule', None),
    ('STRIPE_PORTAL_RETURN_PATH', '/admin/abonnement',
     "Page de retour en sortant du portail de facturation."),

    (None, None, "---------------------------------------------------------------- File d'analyse"),
    ('QUEUE_POLL_MS', '3000',
     "Fréquence de réveil du worker d'analyse, en millisecondes."),
    ('QUEUE_INITIAL_DELAY_MS', '10000',
     "Délai avant la première analyse après un démarrage. Laisse à Ollama le temps de\n"
     "#  charger son modèle : sans ce délai, les premières analyses échouent toutes."),

    (None, None, "---------------------------------------------------------------- Entretien"),
    ('ANALYTICS_PURGE_CRON', '0 30 4 * * *',
     "Purge de la mesure d'audience (plus de 400 jours), chaque nuit à 4 h 30."),
    ('STRIPE_LEDGER_PURGE_CRON', '0 15 4 * * *',
     "Purge des traces de webhooks déjà traités (plus de 30 jours)."),
]


def main() -> None:
    dry = '--dry-run' in sys.argv

    if not ENV.exists():
        sys.exit(f"Fichier introuvable : {ENV}")

    contenu = ENV.read_text(encoding='utf-8', errors='replace')
    presentes = set()
    for line in contenu.splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            presentes.add(line.partition('=')[0].strip())

    ajouts = []
    bloc = []
    titre_en_attente = None

    for var, valeur, commentaire in MANQUANTES:
        if var is None:
            titre_en_attente = commentaire
            continue
        if var in presentes:
            print(f"  {var:28} déjà présent, inchangé")
            continue

        if titre_en_attente:
            bloc.append('')
            bloc.append(f'#  {titre_en_attente}')
            titre_en_attente = None
        if commentaire:
            bloc.append(f'#  {commentaire}')
        bloc.append(f'{var}={valeur}')
        ajouts.append(var)
        print(f"  {var:28} à ajouter = {valeur}")

    if not ajouts:
        print("\nRien à ajouter.")
        return

    if dry:
        print(f"\n{len(ajouts)} variable(s) seraient ajoutées (--dry-run).")
        return

    with ENV.open('a', encoding='utf-8') as f:
        f.write('\n\n#  ======================================================================\n')
        f.write('#  Ajouté par scripts/env-complete.py\n')
        f.write('#  Toutes ces variables ont une valeur par défaut dans application.yaml.\n')
        f.write('#  Les voici explicitées, pour savoir quoi changer en production.\n')
        f.write('#  ======================================================================\n')
        f.write('\n'.join(bloc).lstrip('\n'))
        f.write('\n')

    print(f"\n.env complété : {len(ajouts)} variable(s) ajoutée(s).")


if __name__ == '__main__':
    main()
