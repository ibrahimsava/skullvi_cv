#!/usr/bin/env python3
"""Cree (ou retrouve) les tarifs Skulvi dans le compte Stripe, puis les ecrit dans .env.

Pourquoi un script plutot qu'une saisie a la main dans le tableau de bord :
  - les montants et les libelles doivent correspondre exactement a la grille de
    l'application, sinon le webhook accorde un plan dont le prix affiche ne correspond pas
    a ce qui est preleve ;
  - l'operation est idempotente, grace a une cle de recherche posee en metadonnee : relancer
    le script ne cree pas un second jeu de tarifs ;
  - en mode test comme en mode reel, la meme commande produit le meme resultat.

La grille n'est PAS recopiee ici : elle est lue sur l'application, par
GET /api/v1/public/plans, qui la sert depuis Plan.java. Une grille recopiee dans ce script
finirait par divercher du code — et un ecart de prix entre la page de tarifs et ce qui est
reellement preleve ne se decouvre qu'au premier encaissement.

Usage :
    python scripts/stripe-setup.py            # cree ce qui manque, ecrit les ids dans .env
    python scripts/stripe-setup.py --dry-run  # montre ce qui serait fait, sans rien creer

    BACKEND_URL=http://localhost:18080 python scripts/stripe-setup.py   # autre instance

Le backend doit tourner : sans lui, le script s'arrete plutot que d'inventer des montants.
La cle est lue dans .env et n'est jamais affichee.
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
API = 'https://api.stripe.com/v1'

MARKER = 'skulvi_plan'          # metadonnee qui rend l'operation idempotente

# Meme version d'API que celle figee par la bibliotheque Stripe du backend
# (com.stripe.ApiVersion.CURRENT, cf. la version de stripe-java dans pom.xml). Deux versions
# differentes dans un meme projet, c'est un champ qui change de place sans qu'on le voie.
STRIPE_VERSION = '2025-04-30.basil'

# Prefixe des produits chez Stripe : « Starter » seul serait indechiffrable dans un compte
# qui vend plusieurs services. Purement cosmetique, cote tableau de bord Stripe.
PRODUCT_PREFIX = 'Skulvi'

# Code fiscal Stripe par defaut : « Software as a service (SaaS) - business use ».
#
# Obligatoire des que « Managed Payments » est actif sur le compte, et de toute facon
# necessaire pour que Stripe calcule la TVA correctement : un abonnement logiciel vendu a
# une entreprise de l'Union europeenne ne suit pas les memes regles qu'un bien physique.
# Sans ce code, l'ouverture d'une session de paiement est refusee.
#
# Reglable par STRIPE_TAX_CODE : vendre aux particuliers releve d'un autre code
# (txcd_10103000, « SaaS - personal use »), et la TVA calculee n'est pas la meme.
TAX_CODE_DEFAUT = 'txcd_10103001'
CURRENCY_DEFAUT = 'eur'


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


# ---------------------------------------------------------------- grille tarifaire

def limite(valeur: int | None, singulier: str, pluriel: str) -> str:
    """« 5 offres ouvertes » ou « offres ouvertes sans limite ».

    L'API renvoie null pour une limite absente : c'est la convention retenue cote serveur
    pour distinguer « illimite » de « zero », que Integer.MAX_VALUE rendrait illisible.
    """
    if valeur is None:
        return f'{pluriel} sans limite'
    return f'{valeur} {singulier if valeur == 1 else pluriel}'


def lire_grille() -> list[dict]:
    """Lit les plans payants sur l'application, dans l'ordre ou elle les presente."""
    base = os.environ.get('BACKEND_URL', 'http://localhost:8080').rstrip('/')
    url = f'{base}/api/v1/public/plans'

    try:
        with urllib.request.urlopen(url, timeout=15) as reponse:
            catalogue = json.loads(reponse.read())
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as e:
        sys.exit(
            f"Grille tarifaire illisible sur {url} : {getattr(e, 'reason', e)}\n"
            "Démarrez le backend, ou indiquez son adresse :\n"
            "    BACKEND_URL=http://localhost:18080 python scripts/stripe-setup.py")

    plans = []
    for option in catalogue.get('plans', []):
        if not option.get('priceCents'):
            continue        # plan gratuit : rien a creer chez le prestataire

        plans.append({
            # Meme cle que StripeProperties.prices : le nom du plan en minuscules.
            # C'est elle qui permet au webhook de retrouver le plan d'un abonnement.
            'key': option['plan'].lower(),
            'label': f"{PRODUCT_PREFIX} {option['label']}",
            'cents': option['priceCents'],
            'description': ', '.join((
                limite(option.get('maxActiveOffers'), 'offre ouverte', 'offres ouvertes'),
                limite(option.get('maxAnalysesPerMonth'), 'analyse de CV par mois',
                       'analyses de CV par mois'),
                limite(option.get('maxSeats'), 'utilisateur', 'utilisateurs'),
            )) + '.',
        })

    if not plans:
        sys.exit("Aucun plan payant dans la grille : rien à créer chez le prestataire.")
    return plans


# ---------------------------------------------------------------- Stripe

def call(key: str, method: str, path: str, data: dict | None = None) -> dict:
    url = f'{API}/{path}'
    body = None
    if data:
        # Stripe attend du form-urlencoded, y compris pour les cles imbriquees.
        body = urllib.parse.urlencode(data).encode()

    request = urllib.request.Request(url, data=body, method=method)
    request.add_header('Authorization', f'Bearer {key}')
    request.add_header('Stripe-Version', STRIPE_VERSION)
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


def ensure_tax_code(key: str, product_id: str, label: str, tax_code: str) -> None:
    """Pose le code fiscal sur un produit qui n'en a pas, ou qui n'a pas le bon.

    Les produits crees avant l'ajout du code fiscal n'en ont pas, et toute session de
    paiement les concernant serait refusee. On repare au passage plutot que de demander de
    tout recreer a la main.
    """
    product = call(key, 'GET', f'products/{product_id}')
    actuel = product.get('tax_code')
    # Stripe renvoie soit l'identifiant, soit l'objet complet selon l'expansion.
    if isinstance(actuel, dict):
        actuel = actuel.get('id')
    if actuel == tax_code:
        return

    call(key, 'POST', f'products/{product_id}', {'tax_code': tax_code})
    print(f"      code fiscal posé sur « {label} »")


def find_price(key: str, plan: dict, currency: str) -> tuple[dict | None, list[dict]]:
    """Cherche le tarif de ce plan, et signale ceux qui ne sont plus a jour.

    Un tarif Stripe est immuable : changer un prix veut dire en creer un nouveau. Les
    anciens sont renvoyes a part pour etre archives, sinon le script les retrouverait a la
    prochaine execution et on finirait par vendre a l'ancien prix.
    """
    query = f"metadata['{MARKER}']:'{plan['key']}'"
    result = call(key, 'GET', f'prices/search?{urllib.parse.urlencode({"query": query})}')

    courant, perimes = None, []
    for price in result.get('data', []):
        if not price.get('active'):
            continue
        if price.get('unit_amount') == plan['cents'] and price.get('currency') == currency:
            courant = courant or price
        else:
            perimes.append(price)
    return courant, perimes


def archive_price(key: str, price: dict, plan: dict) -> None:
    """Retire un tarif perime de la vente.

    Les abonnements deja en cours continuent d'etre preleves a ce tarif : Stripe ne
    rattache pas un abonnement existant a un nouveau prix. C'est le comportement voulu —
    un client ne doit pas voir sa mensualite changer sans l'avoir accepte.
    """
    ancien = price['unit_amount'] / 100
    nouveau = plan['cents'] / 100
    call(key, 'POST', f"prices/{price['id']}", {'active': 'false'})
    print(f"      tarif {price['id']} archivé : {ancien:.2f} € → {nouveau:.2f} € "
          f"(les abonnements en cours restent à {ancien:.2f} €)")


def create_price(key: str, plan: dict, tax_code: str, currency: str) -> dict:
    product = call(key, 'POST', 'products', {
        'name': plan['label'],
        'description': plan['description'],
        'tax_code': tax_code,
        f'metadata[{MARKER}]': plan['key'],
    })

    return call(key, 'POST', 'prices', {
        'product': product['id'],
        'unit_amount': plan['cents'],
        'currency': currency,
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

    tax_code = env.get('STRIPE_TAX_CODE') or TAX_CODE_DEFAUT
    currency = (env.get('STRIPE_CURRENCY') or CURRENCY_DEFAUT).lower()

    mode = 'TEST' if key.startswith('sk_test_') else 'RÉEL'
    print(f"Compte Stripe en mode {mode}. Devise {currency.upper()}, code fiscal {tax_code}.")
    if mode == 'RÉEL' and not dry:
        reponse = input("Mode réel : créer de vrais tarifs facturables ? [oui/non] ")
        if reponse.strip().lower() not in ('oui', 'o', 'yes', 'y'):
            sys.exit("Interrompu.")

    plans = lire_grille()
    print(f"Grille lue sur l'application : {len(plans)} plan(s) payant(s).\n")

    updates = {}
    for plan in plans:
        var = f"STRIPE_PRICE_{plan['key'].upper()}"
        euros = plan['cents'] / 100

        existing, perimes = find_price(key, plan, currency)

        if existing:
            print(f"  {plan['label']:22} {euros:>5.2f} €/mois  déjà présent  {existing['id']}")
            if not dry:
                ensure_tax_code(key, existing['product'], plan['label'], tax_code)
            updates[var] = existing['id']
        elif dry:
            print(f"  {plan['label']:22} {euros:>5.2f} €/mois  À CRÉER")
        else:
            price = create_price(key, plan, tax_code, currency)
            print(f"  {plan['label']:22} {euros:>5.2f} €/mois  créé         {price['id']}")
            updates[var] = price['id']

        for ancien in perimes:
            if dry:
                print(f"      tarif {ancien['id']} à archiver "
                      f"({ancien['unit_amount'] / 100:.2f} €, plus à la grille)")
            else:
                archive_price(key, ancien, plan)

    if dry:
        print("\nAucune modification (--dry-run).")
        return

    updates['STRIPE_ENABLED'] = 'true'
    write_env(updates)
    print(f"\n.env mis à jour : {', '.join(sorted(updates))}")
    print("Relancez le backend pour que les tarifs soient pris en compte.")


if __name__ == '__main__':
    main()
