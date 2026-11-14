#!/usr/bin/env python3
"""Verifie la reception des webhooks de paiement, de bout en bout et sans Stripe CLI.

Pourquoi ce script existe
-------------------------
Le webhook est le seul endroit ou l'abonnement d'un client change d'etat : s'il est casse,
un client paie sans rien recevoir. Or c'est aussi le chemin le plus difficile a essayer, car
il faut une adresse publique pour que Stripe puisse appeler le serveur.

Le script contourne le probleme : nous detenons le secret de signature, donc nous pouvons
fabriquer un evenement signe exactement comme Stripe le fait. Ce qui est verifie est donc
bien notre code — verification de signature, idempotence, application du plan — et non la
connectivite reseau avec Stripe.

Ce qui est verifie
------------------
  1. un appel sans en-tete de signature est refuse ;
  2. une signature invalide est refusee ;
  3. un horodatage trop ancien est refuse (protection contre le rejeu) ;
  4. un evenement valide applique le bon plan a la bonne organisation ;
  5. le meme evenement rejoue est ignore, et le plan ne bouge pas.

Usage :
    python scripts/stripe-webhook-test.py
    BACKEND_URL=http://localhost:18080 python scripts/stripe-webhook-test.py

Aucun secret n'est affiche. Le script cree une organisation de test a chaque execution.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import pathlib
import random
import sys
import time
import urllib.error
import urllib.request

ENV = pathlib.Path(__file__).resolve().parent.parent / '.env'
BASE = os.environ.get('BACKEND_URL', 'http://localhost:8080').rstrip('/') + '/api/v1'

# Doit rester alignee sur com.stripe.ApiVersion.CURRENT : la bibliotheque refuse de
# deserialiser l'objet d'un evenement annonce dans une autre version, et le webhook
# repondrait alors « objet non deserialisable » sans rien appliquer.
STRIPE_VERSION = '2025-04-30.basil'

# La console Windows est en cp1252 : sans cela, le premier accent ou la premiere fleche
# interrompt le script au milieu du scenario, ce qui donne un echec trompeur.
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ok_count, ko_count = 0, 0


def verdict(libelle: str, reussi: bool, detail: str = '') -> None:
    global ok_count, ko_count
    if reussi:
        ok_count += 1
        print(f"  [ok]    {libelle}")
    else:
        ko_count += 1
        print(f"  [ECHEC] {libelle}" + (f"\n          {detail}" if detail else ''))


# ---------------------------------------------------------------- acces

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


def api(method: str, path: str, data=None, token: str | None = None,
        headers: dict[str, str] | None = None, raw: str | None = None):
    """Appelle le backend. Renvoie (code HTTP, corps)."""
    if raw is not None:
        body = raw.encode('utf-8')
        content_type = 'application/json'
    elif data is not None:
        body = json.dumps(data).encode('utf-8')
        content_type = 'application/json'
    else:
        body, content_type = None, None

    request = urllib.request.Request(f'{BASE}{path}', data=body, method=method)
    if content_type:
        request.add_header('Content-Type', content_type)
    if token:
        request.add_header('Authorization', f'Bearer {token}')
    for k, v in (headers or {}).items():
        request.add_header(k, v)

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            contenu = response.read().decode('utf-8', errors='replace')
            return response.status, contenu
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', errors='replace')
    except urllib.error.URLError as e:
        sys.exit(f"Backend injoignable sur {BASE} : {e.reason}\n"
                 "Démarrez-le, ou indiquez son adresse :\n"
                 "    BACKEND_URL=http://localhost:18080 python scripts/stripe-webhook-test.py")


# ---------------------------------------------------------------- signature

def signe(payload: str, secret: str, horodatage: int | None = None) -> str:
    """Reproduit l'en-tete « Stripe-Signature ».

    Le condensat porte sur « <horodatage>.<corps exact> » : c'est l'horodatage dans
    l'en-tete qui empeche de rejouer indefiniment un ancien appel capture, et le corps
    exact — octet pour octet — qui empeche d'en modifier le contenu.
    """
    t = horodatage if horodatage is not None else int(time.time())
    condensat = hmac.new(secret.encode('utf-8'),
                         f'{t}.{payload}'.encode('utf-8'),
                         hashlib.sha256).hexdigest()
    return f't={t},v1={condensat}'


def evenement(type_: str, objet: dict, event_id: str) -> str:
    """Fabrique le corps d'un evenement, dans la forme exacte envoyee par Stripe."""
    return json.dumps({
        'id': event_id,
        'object': 'event',
        'api_version': STRIPE_VERSION,
        'created': int(time.time()),
        'livemode': False,
        'pending_webhooks': 0,
        'request': {'id': None, 'idempotency_key': None},
        'type': type_,
        'data': {'object': objet},
    }, separators=(',', ':'))


def abonnement(org_id: str, customer_id: str, price_id: str,
               statut: str = 'active') -> dict:
    """Un objet « subscription » minimal mais conforme a ce que lit le controleur."""
    maintenant = int(time.time())
    return {
        'id': f'sub_test_{random.randint(10 ** 10, 10 ** 11)}',
        'object': 'subscription',
        'customer': customer_id,
        'status': statut,
        'cancel_at_period_end': False,
        'currency': 'eur',
        'metadata': {'organizationId': org_id},
        'items': {
            'object': 'list',
            'has_more': False,
            'url': '/v1/subscription_items',
            'data': [{
                'id': f'si_test_{random.randint(10 ** 10, 10 ** 11)}',
                'object': 'subscription_item',
                'quantity': 1,
                'current_period_start': maintenant,
                'current_period_end': maintenant + 30 * 24 * 3600,
                'price': {
                    'id': price_id,
                    'object': 'price',
                    'active': True,
                    'currency': 'eur',
                    'recurring': {'interval': 'month', 'interval_count': 1},
                },
            }],
        },
    }


def envoie(payload: str, signature: str | None):
    entetes = {'Stripe-Signature': signature} if signature is not None else {}
    return api('POST', '/webhooks/stripe', raw=payload, headers=entetes)


# ---------------------------------------------------------------- scenario

def main() -> None:
    env = read_env()
    secret = env.get('STRIPE_WEBHOOK_SECRET', '')
    price_business = env.get('STRIPE_PRICE_BUSINESS', '')

    if not secret:
        sys.exit("STRIPE_WEBHOOK_SECRET est vide dans .env.\n"
                 "Récupérez-le dans Stripe › Développeurs › Webhooks › votre endpoint "
                 "› « Clé secrète de signature ».")
    if not price_business:
        sys.exit("STRIPE_PRICE_BUSINESS est vide : lancez d'abord scripts/stripe-setup.py.")

    print(f"Backend : {BASE}")
    print(f"Secret de signature : présent ({len(secret)} caractères, jamais affiché)\n")

    # --- une organisation de test, pour ne rien toucher aux données existantes
    n = random.randint(10 ** 5, 10 ** 6)
    code, corps = api('POST', '/auth/signup-organization', {
        'organizationName': f'Webhook Test {n}',
        'firstName': 'Test', 'lastName': 'Webhook',
        'email': f'webhook{n}@skulvi.test',
        'password': 'MotDePasse123!',
    })
    if code not in (200, 201):
        sys.exit(f"Inscription impossible (HTTP {code}) : {corps[:300]}")
    token = json.loads(corps)['accessToken']

    code, corps = api('GET', '/org', token=token)
    org = json.loads(corps)
    org_id = org['id']
    print(f"Organisation de test {org_id}, plan initial {org['plan']}.\n")

    print("Refus des appels non authentiques")
    code, corps = envoie(evenement('customer.subscription.updated',
                                   abonnement(org_id, 'cus_test_x', price_business),
                                   f'evt_test_{n}_a'), None)
    verdict("sans en-tête de signature → 400", code == 400, f"HTTP {code} : {corps[:200]}")

    payload = evenement('customer.subscription.updated',
                        abonnement(org_id, 'cus_test_x', price_business),
                        f'evt_test_{n}_b')
    code, corps = envoie(payload, signe(payload, 'whsec_un_secret_qui_nest_pas_le_bon'))
    verdict("signature forgée → 400", code == 400, f"HTTP {code} : {corps[:200]}")

    payload = evenement('customer.subscription.updated',
                        abonnement(org_id, 'cus_test_x', price_business),
                        f'evt_test_{n}_c')
    vieux = int(time.time()) - 3600
    code, corps = envoie(payload, signe(payload, secret, horodatage=vieux))
    verdict("horodatage d'il y a une heure → 400 (anti-rejeu)",
            code == 400, f"HTTP {code} : {corps[:200]}")

    print("\nApplication d'un abonnement")
    event_id = f'evt_test_{n}_ok'
    payload = evenement('customer.subscription.updated',
                        abonnement(org_id, f'cus_test_{n}', price_business),
                        event_id)

    code, corps = envoie(payload, signe(payload, secret))
    verdict("événement signé → 200", code == 200, f"HTTP {code} : {corps[:200]}")

    code, corps = api('GET', '/org', token=token)
    apres = json.loads(corps)
    verdict(f"plan passé à BUSINESS (lu : {apres['plan']})", apres['plan'] == 'BUSINESS')
    verdict("fin de période enregistrée", apres.get('currentPeriodEnd') is not None,
            f"currentPeriodEnd = {apres.get('currentPeriodEnd')}")
    verdict("organisation marquée payante", apres.get('paid') is True)

    print("\nIdempotence : le même événement rejoué")
    code, corps = envoie(payload, signe(payload, secret))
    verdict("même identifiant d'événement → 200", code == 200, f"HTTP {code}")
    verdict("réponse « déjà traité »", 'déjà traité' in corps, f"réponse : {corps[:120]}")

    code, corps = api('GET', '/org', token=token)
    rejoue = json.loads(corps)
    verdict("plan inchangé après rejeu", rejoue['plan'] == apres['plan'])

    print("\nRésiliation")
    payload = evenement('customer.subscription.deleted',
                        abonnement(org_id, f'cus_test_{n}', price_business, statut='canceled'),
                        f'evt_test_{n}_del')
    code, corps = envoie(payload, signe(payload, secret))
    verdict("résiliation → 200", code == 200, f"HTTP {code} : {corps[:200]}")

    code, corps = api('GET', '/org', token=token)
    final = json.loads(corps)
    verdict(f"retour au plan gratuit (lu : {final['plan']})", final['plan'] == 'FREE')

    print(f"\n{ok_count} vérification(s) passée(s), {ko_count} en échec.")
    sys.exit(1 if ko_count else 0)


if __name__ == '__main__':
    main()
