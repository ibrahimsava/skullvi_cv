#!/usr/bin/env python3
"""Verifie — et corrige — l'endpoint webhook declare dans le compte Stripe.

Deux erreurs se glissent systematiquement dans cette configuration, et toutes deux sont
silencieuses : le paiement aboutit chez Stripe, le client est debite, et l'abonnement ne
s'active jamais cote application.

  1. L'ADRESSE INCOMPLETE. Declarer « https://domaine » au lieu de
     « https://domaine/api/v1/webhooks/stripe » envoie les evenements a la racine du site,
     que le serveur frontal sert comme la page d'accueil. Stripe recoit un 200, se croit
     ecoute, et le backend ne voit rien.

  2. LES EVENEMENTS MANQUANTS. Ne cocher que « checkout.session.completed » active le
     premier paiement et rien d'autre : ni le renouvellement, ni le changement de plan, ni
     la resiliation, ni l'echec de prelevement. Un client qui resilie garde donc son plan
     payant indefiniment.

Mettre l'endpoint a jour CONSERVE son secret de signature : STRIPE_WEBHOOK_SECRET reste
valable, il n'y a rien a changer dans .env.

Usage :
    python scripts/stripe-webhook-list.py            # etat des lieux, sans rien modifier
    python scripts/stripe-webhook-list.py --apply    # corrige l'adresse et les evenements

    PUBLIC_URL=https://skulvicv.cloud python scripts/stripe-webhook-list.py --apply
"""
from __future__ import annotations

import json
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'

CHEMIN_WEBHOOK = '/api/v1/webhooks/stripe'

#
# Exactement les evenements traites par StripeWebhookController, et rien de plus.
#
# En cocher d'autres ne casse rien — le controleur repond 200 et les ignore — mais chaque
# evenement inutile est une requete de plus a traiter et une ligne de plus dans le journal.
#
EVENEMENTS = [
    'checkout.session.completed',       # premier paiement validé
    'customer.subscription.created',
    'customer.subscription.updated',    # renouvellement, changement de plan, résiliation programmée
    'customer.subscription.deleted',    # fin effective : retour au plan gratuit
    'invoice.payment_failed',           # prélèvement refusé : passage en impayé
]

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')


def read_env() -> dict[str, str]:
    if not ENV.exists():
        sys.exit(f"Fichier introuvable : {ENV}")
    values = {}
    for line in ENV.read_text(encoding='utf-8', errors='replace').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, _, v = line.partition('=')
            values[k.strip()] = v.strip().strip('"')
    return values


def call(key: str, method: str, path: str, data: dict | None = None) -> dict:
    body = urllib.parse.urlencode(data, doseq=True).encode() if data else None
    request = urllib.request.Request(f'https://api.stripe.com/v1/{path}', data=body, method=method)
    request.add_header('Authorization', f'Bearer {key}')
    if body:
        request.add_header('Content-Type', 'application/x-www-form-urlencoded')
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as e:
        detail = json.loads(e.read() or b'{}').get('error', {}).get('message', e.reason)
        sys.exit(f"Stripe a refusé {method} {path} : {detail}")
    except urllib.error.URLError as e:
        sys.exit(f"Stripe injoignable : {e.reason}")


def adresse_attendue(env: dict[str, str]) -> str:
    """Adresse complete de l'endpoint, deduite du domaine public."""
    base = (os.environ.get('PUBLIC_URL') or env.get('PUBLIC_URL') or '').rstrip('/')
    if not base or 'localhost' in base or '127.0.0.1' in base:
        sys.exit("PUBLIC_URL doit porter le domaine public pour vérifier l'endpoint.\n"
                 "    PUBLIC_URL=https://skulvicv.cloud python scripts/stripe-webhook-list.py")
    return base + CHEMIN_WEBHOOK


def main() -> None:
    appliquer = '--apply' in sys.argv
    env = read_env()
    key = env.get('STRIPE_SECRET_KEY', '')
    if not key:
        sys.exit("STRIPE_SECRET_KEY est vide dans .env.")

    mode = 'TEST' if key.startswith('sk_test_') else 'RÉEL'
    attendue = adresse_attendue(env)
    print(f"Compte Stripe en mode {mode}.")
    print(f"Adresse attendue : {attendue}\n")

    data = call(key, 'GET', 'webhook_endpoints?limit=25').get('data', [])

    if not data:
        print("Aucun endpoint webhook déclaré.")
        print("Sans endpoint, aucun abonnement ne s'activera : le paiement aboutit chez")
        print("Stripe, le client est débité, mais rien ne revient à l'application.\n")
        if not appliquer:
            print("Relancez avec --apply pour en créer un.")
            return

        cree = call(key, 'POST', 'webhook_endpoints',
                    {'url': attendue, 'enabled_events[]': EVENEMENTS})
        print(f"Endpoint créé : {cree['id']}")
        print(f"\nSECRET DE SIGNATURE à reporter dans STRIPE_WEBHOOK_SECRET :")
        print(f"  {cree.get('secret', '(non renvoyé)')}")
        print("\nC'est la seule fois où Stripe le communique. Notez-le maintenant.")
        return

    for e in data:
        url = e.get('url', '')
        events = set(e.get('enabled_events', []))
        manquants = [ev for ev in EVENEMENTS if ev not in events]
        bonne_url = url == attendue

        print(f"  [{e.get('status', '?')}] {url}")
        print(f"     adresse     : {'correcte' if bonne_url else 'INCORRECTE — attendu ' + attendue}")
        if manquants:
            print(f"     événements  : {len(manquants)} MANQUANT(S) — {', '.join(manquants)}")
        else:
            print(f"     événements  : les {len(EVENEMENTS)} nécessaires sont là")
        print(f"     id          : {e.get('id')}")

        if bonne_url and not manquants:
            print("     → rien à corriger.\n")
            continue

        if not appliquer:
            print("     → relancez avec --apply pour corriger.\n")
            continue

        # Mettre l'endpoint a jour conserve son secret : rien a changer dans .env.
        call(key, 'POST', f"webhook_endpoints/{e['id']}",
             {'url': attendue, 'enabled_events[]': EVENEMENTS})
        print("     → corrigé (adresse et événements). Le secret de signature est inchangé.\n")

    if not appliquer:
        print("Aucune modification (mode lecture).")


if __name__ == '__main__':
    main()
