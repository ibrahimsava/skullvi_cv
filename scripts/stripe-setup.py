#!/usr/bin/env python3
"""Cree (ou retrouve) les tarifs Skulvi dans le compte Stripe, puis les ecrit dans .env.

Pourquoi un script plutot qu'une saisie a la main dans le tableau de bord :
  - les montants et les libelles doivent correspondre exactement a Plan.java, sinon le
    webhook accorde un plan dont le prix affiche ne correspond pas a ce qui est preleve ;
  - l'operation est idempotente, grace a une cle de recherche posee en metadonnee : relancer
    le script ne cree pas un second jeu de tarifs ;
  - en mode test comme en mode reel, la meme commande produit le meme resultat.

Usage :
    python scripts/stripe-setup.py            # cree ce qui manque, ecrit les ids dans .env
    python scripts/stripe-setup.py --dry-run  # montre ce qui serait fait, sans rien creer

La cle est lue dans .env et n'est jamais affichee.
"""
from __future__ import annotations

import json
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request

ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'
API = 'https://api.stripe.com/v1'

# Doit correspondre a Plan.java. La cle de gauche est celle attendue par la configuration
# (STRIPE_PRICE_<CLE>), et sert aussi de metadonnee de recherche chez Stripe.
PLANS = [
    {'key': 'starter',  'label': 'Skulvi Starter',  'cents':  900,
     'description': "5 offres ouvertes, 250 analyses de CV par mois, 4 utilisateurs."},
    {'key': 'pro',      'label': 'Skulvi Pro',      'cents': 1400,
     'description': "15 offres ouvertes, 800 analyses de CV par mois, 10 utilisateurs."},
    {'key': 'business', 'label': 'Skulvi Illimité', 'cents': 2000,
     'description': "Offres, analyses de CV et utilisateurs sans limite."},
]

CURRENCY = 'eur'
MARKER = 'skulvi_plan'          # metadonnee qui rend l'operation idempotente


# ---------------------------------------------------------------- .env

def read_env() -> dict[str, str]:
    if not ENV.exists():
        sys.exit(f"Fichier introuvable : {ENV}")
    values = {}
    for line in ENV.read_text(encoding='utf-8', errors='replace').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, _, v = line.partition('=')
            values[k.strip()] = v.strip()
    return values


def write_env(updates: dict[str, str]) -> None:
    """Met a jour les cles existantes en place, ajoute celles qui manquent a la fin."""
    lines = ENV.read_text(encoding='utf-8', errors='replace').splitlines()
    remaining = dict(updates)

    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped or stripped.startswith('#') or '=' not in stripped:
            continue
        key = stripped.partition('=')[0].strip()
        if key in remaining:
            lines[i] = f'{key}={remaining.pop(key)}'

    if remaining:
        lines.append('')
        lines.append('#  Ajoute par scripts/stripe-setup.py')
        for k, v in remaining.items():
            lines.append(f'{k}={v}')

    ENV.write_text('\n'.join(lines) + '\n', encoding='utf-8')


# ---------------------------------------------------------------- Stripe

def call(key: str, method: str, path: str, data: dict | None = None) -> dict:
    url = f'{API}/{path}'
    body = None
    if data:
        # Stripe attend du form-urlencoded, y compris pour les cles imbriquees.
        body = urllib.parse.urlencode(data).encode()

    request = urllib.request.Request(url, data=body, method=method)
    request.add_header('Authorization', f'Bearer {key}')
    request.add_header('Stripe-Version', '2024-06-20')
    if body:
        request.add_header('Content-Type', 'application/x-www-form-urlencoded')

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as e:
        detail = json.loads(e.read() or b'{}').get('error', {}).get('message', e.reason)
        # La cle n'apparait jamais dans le message.
        sys.exit(f"Stripe a refusé {method} {path} : {detail}")
    except urllib.error.URLError as e:
        sys.exit(f"Stripe injoignable : {e.reason}")


def find_price(key: str, plan: dict) -> dict | None:
    """Cherche un tarif deja cree par ce script, via sa metadonnee."""
    query = f"metadata['{MARKER}']:'{plan['key']}'"
    result = call(key, 'GET', f'prices/search?{urllib.parse.urlencode({"query": query})}')
    for price in result.get('data', []):
        if price.get('active') and price.get('unit_amount') == plan['cents'] \
                and price.get('currency') == CURRENCY:
            return price
    return None


def create_price(key: str, plan: dict) -> dict:
    product = call(key, 'POST', 'products', {
        'name': plan['label'],
        'description': plan['description'],
        f'metadata[{MARKER}]': plan['key'],
    })

    return call(key, 'POST', 'prices', {
        'product': product['id'],
        'unit_amount': plan['cents'],
        'currency': CURRENCY,
        'recurring[interval]': 'month',
        'nickname': f"{plan['label']} — mensuel",
        f'metadata[{MARKER}]': plan['key'],
    })


# ---------------------------------------------------------------- principal

def main() -> None:
    dry = '--dry-run' in sys.argv
    env = read_env()

    key = env.get('STRIPE_SECRET_KEY', '')
    if not key:
        sys.exit("STRIPE_SECRET_KEY est vide dans .env.")

    mode = 'TEST' if key.startswith('sk_test_') else 'RÉEL'
    print(f"Compte Stripe en mode {mode}.")
    if mode == 'RÉEL' and not dry:
        reponse = input("Mode réel : créer de vrais tarifs facturables ? [oui/non] ")
        if reponse.strip().lower() not in ('oui', 'o', 'yes', 'y'):
            sys.exit("Interrompu.")

    updates = {}
    for plan in PLANS:
        var = f"STRIPE_PRICE_{plan['key'].upper()}"
        euros = plan['cents'] / 100

        existing = find_price(key, plan)
        if existing:
            print(f"  {plan['label']:22} {euros:>5.2f} €/mois  déjà présent  {existing['id']}")
            updates[var] = existing['id']
            continue

        if dry:
            print(f"  {plan['label']:22} {euros:>5.2f} €/mois  À CRÉER")
            continue

        price = create_price(key, plan)
        print(f"  {plan['label']:22} {euros:>5.2f} €/mois  créé         {price['id']}")
        updates[var] = price['id']

    if dry:
        print("\nAucune modification (--dry-run).")
        return

    updates['STRIPE_ENABLED'] = 'true'
    write_env(updates)
    print(f"\n.env mis à jour : {', '.join(sorted(updates))}")
    print("Relancez le backend pour que les tarifs soient pris en compte.")


if __name__ == '__main__':
    main()
