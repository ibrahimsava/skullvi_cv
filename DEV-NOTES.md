# Skulvi : notes de développement et aide-mémoire

Stack : front **Angular 22**, back **Spring Boot 4 (JWT)**, base **PostgreSQL 16** avec
**Flyway**, analyse de CV par **Ollama** (modèle `qwen2.5:3b`), le tout orchestré avec
**Docker Compose**.

Depuis le passage en SaaS, l'application est **multi-client** : chaque entreprise a son
organisation, son équipe, son abonnement et ses données, invisibles des autres.

---

## 1. Les trois espaces

| Espace | Qui | Routes front | Ce qu'on y fait |
|---|---|---|---|
| **Public** | visiteurs | `/offres`, `/c/:slug`, `/tarifs`, `/postuler/:id` | consulter les offres, postuler |
| **Candidat** | compte sans organisation | `/mon-espace` | suivre ses candidatures, voir son CV analysé, retirer un dossier |
| **Recruteur** | membre d'une organisation | `/admin/**` | offres, candidatures, classement, équipe, abonnement |

Il existe en plus un **compte administrateur de plateforme** (`ADMIN_EMAIL` / `ADMIN_PASSWORD`),
qui gère les abonnements des clients via `/api/v1/platform/**`. Il n'appartient à aucune
organisation : il ne peut donc lire **aucune** candidature ni **aucun** CV de client. C'est
voulu, et vérifié par un test.

---

## 2. Comment le cloisonnement fonctionne

C'est le point le plus important du code. L'organisation est inscrite **dans le jeton JWT**
(claim `org`) et **nulle part ailleurs** :

```java
// AuthService.tokenFor()
claims.claim("org", membership.getOrganization().getId().toString());
claims.claim("orgRole", membership.getRole().name());
```

Tous les services la lisent via `CurrentUser.orgId()`, et **aucune route ne prend un
identifiant d'organisation en paramètre**. Conséquence : un recruteur ne peut pas atteindre les
données d'un autre client, même en forgeant une URL. Chaque requête SQL est filtrée :

```java
// 404 et non 403 : répondre « interdit » confirmerait l'existence de l'offre,
// et permettrait d'énumérer celles de la concurrence.
repository.findByIdAndOrganizationId(id, orgId)
```

Les repositories n'exposent volontairement **pas** de méthode par identifiant seul. C'est une
garantie structurelle : un appelant distrait ne *peut pas* servir la donnée d'un autre client.

Ce qu'il ne faut jamais faire :

```java
// ❌ accepte un orgId venant du client
public List<Offer> list(@RequestParam UUID organizationId)

// ❌ ignore le cloisonnement
repository.findById(id)
```

---

## 3. Modèle économique dans le code

L'unité facturée est l'**analyse de CV** : c'est à la fois le coût réel (temps CPU du modèle)
et la valeur perçue par le recruteur.

Les limites sont dans `Plan.java`, pas en base — une limite est une règle métier, elle doit
être versionnée avec le code et testable :

| Plan | Prix | Analyses / mois | Offres ouvertes | Sièges | Priorité file |
|---|---:|---:|---:|---:|---:|
| **Gratuit** | 0 € | 40 | 2 | 2 | 0 |
| Starter | 9 € | 250 | 5 | 4 | 1 |
| Pro | 14 € | 800 | 15 | 10 | 2 |
| Illimité | 20 € | illimité | illimité | illimité | 3 |

Dépassement : **0,10 € par analyse** (`Plan.OVERAGE_CENTS`), sur les plans payants non
illimités. Détail du palier gratuit en section 10.

Cette grille est **la seule source de vérité des prix**. Le front ne connaît aucun montant :
il affiche `priceCents` servi par `/public/plans`, `/org/plans` et `/platform/organizations`.
`scripts/stripe-setup.py` la lit par HTTP au lieu de la recopier. Changer un prix se fait donc
dans `Plan.java`, puis en relançant le script.

> **Marge à surveiller sur l'illimité.** 20 €/mois pour des analyses sans limite, avec un
> modèle qui tourne sur notre propre CPU, n'a pas de plafond de coût : un client qui verse
> 10 000 CV coûte plus qu'il ne rapporte. Ce qui protège les autres clients aujourd'hui, c'est
> la **priorité de file** (`Plan.queuePriority`), pas le prix — leurs analyses passent devant.
> Si le cas se présente, le levier est une limite de débit journalière sur l'illimité, pas une
> coupure.

Deux règles à retenir :

- **Un plan payant ne se coupe jamais au quota.** Le dépassement est facturé à l'unité. Couper
  le service d'un client en pleine campagne de recrutement serait absurde.
- **Le plan gratuit s'arrête à son quota**, mais le compteur repart le 1er du mois. C'est ce
  qui transforme le gratuit en abonnement, sans jamais bloquer définitivement un compte.

`QuotaService` est la porte d'entrée unique de toutes ces règles, pour que la question
« ce client a-t-il le droit de faire ça ? » ait toujours la même réponse.

### Activer un plan à la main (paiement hors ligne)

Le paiement en ligne est branché (section 11). Ces routes restent utiles pour un virement,
un geste commercial, ou tant que le compte du prestataire n'est pas ouvert.

```powershell
# 1. se connecter avec le compte admin de la plateforme
$t = (curl.exe -s -X POST https://<domaine>/api/v1/auth/login `
      -H "Content-Type: application/json" `
      -d '{"email":"<ADMIN_EMAIL>","password":"<ADMIN_PASSWORD>"}' | ConvertFrom-Json).accessToken

# 2. lister les clients et leur consommation
curl.exe -s https://<domaine>/api/v1/platform/organizations -H "Authorization: Bearer $t"

# 3. activer le plan après encaissement
curl.exe -s -X PUT "https://<domaine>/api/v1/platform/organizations/<id>/plan?plan=PRO" `
  -H "Authorization: Bearer $t"
```

Le changement prend effet **immédiatement** : le quota est lu en base à chaque requête, le
client n'a pas besoin de se reconnecter.

---

## 4. File d'analyse

Table `analysis_job`, consommée par un **worker unique** (`AnalysisQueue.drain()`, réveillé
toutes les 3 secondes).

Pourquoi un seul worker : un seul modèle Ollama tourne à la fois. Lancer deux analyses en
parallèle sur le même CPU ne les rend pas plus rapides, ça les rend toutes les deux lentes. Le
débit se règle en ajoutant des conteneurs, pas des threads.

Points de conception :

- **Persistée en base**, plus en mémoire. L'ancienne file perdait silencieusement toutes les
  analyses en attente à chaque redéploiement, et un candidat dont le CV n'était jamais traité
  n'avait aucun moyen de le savoir.
- **Reprise au démarrage** : `requeueInterrupted()` remet les tâches `RUNNING` en `PENDING`.
  Sans cela une candidature restait bloquée en `ANALYZING` pour toujours.
- **Priorité par plan** : un client qui paie passe devant un compte gratuit quand la file est
  chargée.
- **Verrou `FOR UPDATE SKIP LOCKED`** : si un second conteneur backend tourne un jour, les deux
  workers ne prendront jamais la même tâche — sinon le même CV serait analysé et facturé deux fois.
- **Une relance manuelle n'est pas facturée** (`billable = false`) : l'échec vient le plus
  souvent de nous.

Diagnostic :

```sql
-- état de la file
select status, count(*) from analysis_job group by status;

-- les échecs définitifs et leur motif
select application_id, attempts, last_error from analysis_job where status = 'FAILED';

-- consommation du mois par client
select o.name, u.period, u.used
from usage_record u join organization o on o.id = u.organization_id
order by u.used desc;
```

---

## 5. Migrations Flyway

`ddl-auto: validate` et non plus `update` : c'est Flyway qui fait évoluer le schéma, Hibernate
vérifie au démarrage que la base correspond aux entités. Une migration oubliée fait échouer le
démarrage, au lieu de laisser Hibernate modifier la base d'un client en silence.

Trois fichiers dans `src/main/resources/db/migration/` :

- **`V1__schema_initial.sql`** : le schéma *tel qu'il était avant* le multi-client.
- **`V2__multi_client_et_facturation.sql`** : organisations, équipes, quotas, file d'analyse.
- **`V3__plan_gratuit_paiement_et_audience.sql`** : plan gratuit permanent, colonnes de
  paiement, table de mesure d'audience.
- **`V4__idempotence_webhooks.sql`** : table `processed_event`, qui garantit qu'un événement
  de paiement livré deux fois n'est appliqué qu'une fois.

Pourquoi partir de l'ancien schéma : la base de production a été créée par Hibernate en mode
`update` et n'a aucun historique Flyway. Avec `baseline-on-migrate: true`, Flyway la considère
comme déjà à la version 1 et saute V1, pour n'appliquer que les suivantes. Une base vide,
elle, déroule V1 à V4 et arrive au même état. **Les deux chemins convergent**, sans jamais
rejouer une création de table existante.

V2 reprend aussi les données existantes : elle crée une organisation « Skulvi » en plan
`BUSINESS`/`ACTIVE`, y rattache toutes les offres, candidats et candidatures, et fait des
comptes `ADMIN`/`RECRUTEUR` existants ses propriétaires. Un déploiement qui tournait déjà ne se
retrouve donc pas bloqué par un quota du jour au lendemain.

> **Avant de déployer sur la base de production** : faire une sauvegarde
> (`scripts/backup.sh`), puis vérifier dans les logs de démarrage que Flyway affiche
> `Successfully applied … migrations` et qu'Hibernate ne lève pas de `Schema-validation`.
> Si `validate` échoue, c'est qu'une colonne générée par Hibernate diffère de V1 — le
> corriger par une nouvelle migration, jamais à la main.

Ajouter une migration :

```
src/main/resources/db/migration/V3__ce_que_ca_fait.sql
```

Ne jamais modifier une migration déjà appliquée : Flyway en vérifie l'empreinte et refusera de
démarrer.

---

## 6. Commandes utiles

### Docker

```powershell
docker compose up -d --build      # reconstruit le backend et relance tout
docker compose up -d              # relance sans reconstruire (changement de .env seulement)
docker compose down               # arrête les conteneurs (les volumes sont conservés)
docker logs -f springboot-app     # suit les logs du backend
docker logs --tail 30 springboot-app
docker exec -it springboot-app printenv OLLAMA_MODEL
docker exec -it springboot-app ls -la /data/cvs
```

> `--build` est obligatoire dès qu'on modifie du code Java, `application.yaml` ou une migration
> (tout cela est dans le jar).

### Configuration : où va quoi

```powershell
python scripts/env-complete.py --dry-run   # ce qui manque dans .env
python scripts/env-complete.py             # l'ajoute, commenté, sans rien écraser
```

Trois endroits, et un seul par variable :

| Endroit | Ce qui y va | Pourquoi |
|---|---|---|
| `.env` | tout ce qui change d'une instance à l'autre : secrets, adresses, réglages | jamais versionné (`git check-ignore .env` doit répondre), lu par `compose.yaml` via `env_file` |
| `compose.yaml`, bloc `environment` | `OLLAMA_URL`, `CV_STORAGE_DIR` | leur valeur **diffère** entre le conteneur (`http://ollama:11434`, `/data/cvs`) et une exécution locale. Les mettre dans `.env` casserait l'un ou l'autre |
| `application.yaml` | la valeur **par défaut** de chaque variable | l'application démarre sans `.env`, et le fichier sert d'inventaire lisible de ce qui est réglable |

Toute variable de `application.yaml` a une valeur par défaut : rien n'est obligatoire sauf
`DB_*` et `JWT_SECRET`. `env-complete.py` les écrit quand même explicitement, avec un
commentaire, pour qu'on voie d'un coup d'œil ce qu'il faudra changer en production.

> **Une expression cron se met entre guillemets** dans `.env` : elle contient des espaces, et
> un shell qui sourcerait le fichier essaierait d'exécuter les champs suivants comme des
> commandes. C'est arrivé.

### Ollama

```powershell
docker exec -it ollama ollama pull qwen2.5:3b     # une seule fois, gardé dans le volume
docker exec -it ollama ollama list
docker exec -it ollama ollama run qwen2.5:3b "Dis bonjour"
```

Le port 11434 n'est plus publié : Ollama n'a aucune authentification, l'exposer revient à
offrir un service d'inférence ouvert à tous. Le backend l'atteint par le réseau interne du
compose.

### Tests

```powershell
./mvnw test                              # 87 tests backend, sans base de données
cd frontSkulvi; npx ng test --watch=false   # 26 tests front
```

Aucun test n'a besoin de PostgreSQL ni d'Ollama : les règles de quota, la génération des slugs,
le limiteur de débit et les libellés candidat sont testés en isolation. L'ancien
`contextLoads` a été retiré, il exigeait une base et ne vérifiait rien d'utile.

Deux vérifications demandent en revanche une instance qui tourne, parce qu'elles portent sur
ce qui ne se teste pas en isolation — la chaîne complète jusqu'au prestataire de paiement :

```powershell
python scripts/stripe-webhook-test.py    # 12 vérifications, cf. section 11
python scripts/stripe-setup.py --dry-run # la grille Stripe correspond-elle au code ?
```

### Vérifier le cloisonnement à la main

Le test qui compte, à refaire après toute modification de sécurité :

```bash
A=$(curl -s -X POST $API/auth/signup-organization -H 'Content-Type: application/json' \
  -d '{"organizationName":"Entreprise A","firstName":"A","lastName":"A","email":"a@a.fr","password":"motdepasse1"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

B=$(curl -s -X POST $API/auth/signup-organization -H 'Content-Type: application/json' \
  -d '{"organizationName":"Entreprise B","firstName":"B","lastName":"B","email":"b@b.fr","password":"motdepasse2"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# A crée une offre, B doit recevoir 404 sur tout
OFFER=$(curl -s -X POST $API/offers -H "Authorization: Bearer $A" -H 'Content-Type: application/json' \
  -d '{"title":"Test","description":"x","criteria":[{"name":"Java","type":"SKILL","mandatory":true,"weight":40}]}' \
  | grep -oE '"id":"[0-9a-f-]{36}"' | head -1 | cut -d'"' -f4)

curl -s -o /dev/null -w "doit etre 404 : %{http_code}\n" $API/offers/$OFFER -H "Authorization: Bearer $B"
curl -s -o /dev/null -w "doit etre 404 : %{http_code}\n" $API/offers/$OFFER/applications -H "Authorization: Bearer $B"
curl -s -o /dev/null -w "doit etre 404 : %{http_code}\n" $API/offers/$OFFER/ranking -H "Authorization: Bearer $B"
```

### Front

```powershell
ng serve                       # http://localhost:4200
ng serve --host 0.0.0.0        # accessible depuis le téléphone (même Wi-Fi)
ipconfig                       # trouver l'IPv4 du PC
```

---

## 7. Problèmes rencontrés et solutions

| Problème | Cause | Solution |
|---|---|---|
| Flyway ne s'exécute pas, aucune trace dans les logs | En Spring Boot 4, les autoconfigurations sont dans des modules dédiés. `flyway-core` seul est présent mais jamais déclenché | Utiliser `spring-boot-starter-flyway` |
| « Schema validation: missing table » au démarrage | Flyway inactif, ou migration non écrite pour une nouvelle entité | Vérifier le starter, puis ajouter la migration |
| Un recruteur voit les candidatures d'un autre client | Lecture par `findById` sans filtre d'organisation | Toujours `findByIdAndOrganizationId` |
| Le tableau de bord affiche les chiffres de toute la plateforme | Compteurs non filtrés | `countByOrganizationId...` |
| `2147483647 offres` affiché dans l'interface | La sentinelle `Integer.MAX_VALUE` était sérialisée telle quelle | Les DTO envoient `null` pour « illimité », le front écrit « Illimité » |
| CORS ne marche qu'avec une seule origine | `List.of(origin)` ne découpait pas les virgules | `origins.split(",")` dans `SecurityConfig` |
| Le front local refuse de parler au backend | Origines différentes en local, et port du serveur de développement instable | Mandataire `proxy.conf.cjs` : même origine, plus de CORS (section 9) |
| Candidature bloquée en `ANALYZING` après un redéploiement | File en mémoire, tâches perdues | File en base + `requeueInterrupted()` au démarrage |
| Le candidat choisit un `.docx`, l'envoi échoue | Le formulaire acceptait Word, le serveur vérifie la signature PDF | `accept="application/pdf"` et contrôle avant envoi |
| Règle SCSS sans effet dans `dashboard.scss` | `&.alert strong` écrit à la racine du fichier, sans parent à résoudre | Remise dans `.stat` |
| « Service IA indisponible (Ollama) : Connection refused » | Ollama non lancé ou inaccessible depuis Docker | Service `ollama` dans le compose, URL `http://ollama:11434` |
| Analyse toujours en échec alors qu'Ollama répond | Le backend demandait `qwen2.5:7b`, seul le `3b` était installé | `OLLAMA_MODEL: qwen2.5:3b` et reconstruction |
| `WARN No Unicode mapping for ... FontAwesome` | Icônes du CV que PDFBox ne convertit pas | Sans gravité, le texte normal est extrait |

---

## 8. Pièges à retenir

- Une valeur écrite dans `application.yaml` est **dans le jar** : reconstruire l'image.
- Le nom du modèle Ollama doit être **exactement** le même côté backend et côté `ollama pull`.
- Le premier appel à Ollama est lent (chargement du modèle). Le délai initial de la file
  (`QUEUE_INITIAL_DELAY_MS`) existe pour ça.
- Sans carte graphique, `qwen2.5:3b` tourne correctement. Le `7b` demande ~8 Go de RAM et reste
  lent sur CPU.
- Une méthode `@Transactional` appelée **depuis la même classe** ne passe pas par le proxy
  Spring : son annotation est purement décorative. C'est pour cette raison que
  `AnalysisJobStore` est un bean distinct de `AnalysisQueue`.
- Ne jamais rattraper une violation de contrainte unique au milieu d'une transaction
  PostgreSQL : la transaction est condamnée, tout ce qui suit échouera. Préférer un
  `INSERT ... ON CONFLICT` (voir `UsageRepository.recordOne`).
- Le limiteur de débit compte **en mémoire**. Avec plusieurs conteneurs backend, le plafond réel
  est multiplié par leur nombre. Tant qu'un seul tourne, c'est exact ; au-delà, déplacer les
  compteurs dans Redis.
- Les CV contiennent des données personnelles : garder l'analyse sur sa propre infrastructure,
  ne pas exposer Ollama, supprimer les fichiers avec la candidature.
- Ne jamais pousser `.env` (secret JWT, mots de passe).

---

## 9. Développer en local : plus aucun problème de CORS

C'était le symptôme : le front local refusait de parler au backend. La cause n'était pas une
mauvaise configuration mais une différence de chemin entre local et production.

| | Origine du front | URL appelée | CORS ? |
|---|---|---|---|
| **Production** | `https://domaine` | `/api/v1` (relative) | non, même origine |
| **Local, avant** | `http://localhost:<port>` | `http://localhost:8080/api/v1` | oui, et bloqué |

Et le port local n'est pas stable : une variable d'environnement `PORT` présente sur la
machine force `ng serve` ailleurs que sur 4200, donc l'origine n'était jamais dans la liste
autorisée côté serveur.

La correction supprime le CORS en local au lieu de l'élargir. Un **mandataire** dans le
serveur de développement (`frontSkulvi/proxy.conf.cjs`) relaie `/api` vers le backend, et
`environment.ts` utilise désormais `/api/v1` comme la production. Le navigateur n'appelle
donc que son propre serveur.

```powershell
ng serve                                                 # backend attendu sur localhost:8080
$env:BACKEND_URL="http://localhost:18080"; ng serve      # backend sur un autre port
$env:BACKEND_URL="http://192.168.1.20:8080"; ng serve    # backend sur une autre machine
```

> **La production n'est pas touchée.** `environment.prod.ts` utilisait déjà `/api/v1`, le
> mandataire ne concerne que `ng serve`, et `CORS_ORIGIN` garde exactement le même
> comportement pour une origine exacte.

En complément, `CORS_ORIGIN` accepte maintenant des motifs : une entrée contenant une étoile
(`http://localhost:*`) passe par `setAllowedOriginPatterns`. Utile pour tester depuis un
téléphone sur le même réseau sans lister les ports un par un. Sans danger ici parce que
`allowCredentials` reste désactivé — le jeton voyage dans un en-tête, pas dans un cookie.

---

## 10. Plan gratuit permanent

L'essai de 14 jours a disparu. À la place, un **palier gratuit sans échéance** :

| Plan | Prix | Analyses / mois | Offres ouvertes | Sièges |
|---|---:|---:|---:|---:|
| **Gratuit** | 0 € | 40 | 2 | 2 |
| Starter | 29 € | 200 | 5 | 4 |
| Pro | 99 € | 900 | 20 | 12 |
| Business | 299 € | 4 000 | illimité | illimité |

Le raisonnement : un essai qui expire oblige à décider avant d'avoir eu un vrai besoin. Un
palier gratuit permanent laisse l'entreprise s'installer, et c'est le quota mensuel qui
déclenche la conversion — pas un compte à rebours.

Conséquences dans le code :

- `OrgStatus` se réduit à `ACTIVE` / `PAST_DUE` / `CANCELED`, et **aucun statut ne bloque
  l'accès**. Un impayé redescend aux limites du gratuit, il ne coupe pas le service : couper
  fait perdre le client, pas gagner le paiement.
- `TrialSweeper` a été supprimé, il n'y a plus d'échéance à surveiller.
- La migration `V3` passe les organisations `TRIAL` en `FREE` et les `SUSPENDED` en `ACTIVE`,
  sans rien supprimer.

---

## 11. Paiement par abonnement

Branché de bout en bout, et **inactif tant que les clés ne sont pas renseignées** :
`StripeProperties.usable()` répond faux, l'interface affiche un contact au lieu d'un bouton
qui échouerait, et l'application démarre normalement.

```
STRIPE_ENABLED=true
STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_STARTER=price_...        # écrits par scripts/stripe-setup.py
STRIPE_PRICE_PRO=price_...
STRIPE_PRICE_BUSINESS=price_...
STRIPE_CURRENCY=eur                   # lues par le script de configuration
STRIPE_TAX_CODE=txcd_10103001         # « SaaS - business use »
PUBLIC_URL=https://votre-domaine
```

### Créer les tarifs chez Stripe

```powershell
python scripts/stripe-setup.py --dry-run   # ce qui serait fait
python scripts/stripe-setup.py             # crée ce qui manque, écrit les ids dans .env
```

**La grille n'est écrite qu'une fois, dans `Plan.java`.** Le script ne la recopie pas : il la
lit sur l'application par `GET /api/v1/public/plans`, et s'arrête si le backend ne répond
pas plutôt que d'inventer des montants. Un prix recopié dans un script finit par divercher du
code, et l'écart entre la page de tarifs et ce qui est réellement prélevé ne se découvre qu'au
premier encaissement.

Le script est idempotent (métadonnée `skulvi_plan`) et **archive les tarifs périmés** : un
tarif Stripe étant immuable, changer un prix veut dire en créer un nouveau. Les abonnements
déjà en cours continuent d'être prélevés à l'ancien tarif — un client ne doit pas voir sa
mensualité changer sans l'avoir accepté.

> **Le code fiscal n'est pas optionnel.** Sans `tax_code` sur le produit, Stripe refuse
> d'ouvrir la session de paiement dès que « Managed Payments » est actif — ce qui est le cas
> par défaut sur un compte récent. Le message d'erreur est explicite mais arrive en 503 côté
> client. `txcd_10103001` pour une vente aux entreprises, `txcd_10103000` aux particuliers.

| Brique | Rôle |
|---|---|
| `StripeService` | parle au prestataire : session de paiement, portail, signature des webhooks |
| `SubscriptionService` | applique l'état sur l'organisation — seul endroit qui écrit le plan |
| `PriceCatalog` | correspondance tarif vers plan, construite une fois au démarrage |
| `StripeWebhookController` | reçoit les événements signés |
| `BillingController` | routes client : `checkout`, `portal`, `status` |

Quatre points structurants :

1. **Passage en caisse hébergé.** Le client saisit sa carte sur une page du prestataire.
   Aucune donnée bancaire ne traverse ce serveur, ce qui nous met hors du périmètre PCI-DSS.
2. **Le webhook est la seule source de vérité.** Le retour du navigateur après paiement ne
   fait pas foi : l'utilisateur peut fermer l'onglet avant la redirection, ou forger l'URL de
   succès. Seul l'événement signé change le plan.
3. **L'identifiant d'organisation voyage en métadonnée** de l'abonnement, et non dans l'URL.
   Au renouvellement, des mois plus tard, c'est la seule chose dont on a besoin.
4. **Idempotence dans les deux sens** — détaillée juste en dessous.

### Idempotence

| Sens | Mécanisme | Ce qu'il évite |
|---|---|---|
| Sortant | `Idempotency-Key` sur `checkout` (org + plan + minute) et sur la création du client (org) | un double-clic ouvrant deux sessions, donc deux abonnements pour un seul client |
| Entrant | table `processed_event`, `WebhookLedger.claim()` en `INSERT ... ON CONFLICT DO NOTHING` | le même changement appliqué deux fois, le prestataire garantissant « au moins une fois » |

Sur un doublon entrant, la réponse est **200 et non une erreur** : un code d'échec
relancerait une nouvelle tentative pour un événement déjà honoré. À l'inverse, une erreur de
notre côté retire la trace (`ledger.release`) et renvoie 500, pour que la nouvelle tentative
soit bien traitée au lieu d'être prise pour un doublon. Purge à 30 jours
(`STRIPE_LEDGER_PURGE_CRON`).

### Tester les webhooks sans Stripe CLI

```powershell
python scripts/stripe-webhook-test.py
$env:BACKEND_URL = 'http://localhost:18080'; python scripts/stripe-webhook-test.py
```

Stripe doit pouvoir appeler le serveur pour livrer un webhook, ce qui demande une adresse
publique — d'où le CLI, ou un tunnel. Le script contourne l'obstacle : nous détenons le
secret de signature, donc nous fabriquons un événement signé exactement comme Stripe le
fait. Ce qui est vérifié est donc bien notre code, pas la connectivité réseau.

Douze vérifications, sur une organisation créée pour l'occasion : refus sans en-tête de
signature, refus d'une signature forgée, refus d'un horodatage d'il y a une heure
(anti-rejeu), application du plan, enregistrement de la fin de période, rejeu du même
identifiant d'événement renvoyant « déjà traité » sans rien changer, et retour au gratuit sur
résiliation.

Avec le CLI, si vous l'installez :

```powershell
stripe login
stripe listen --forward-to http://localhost:8080/api/v1/webhooks/stripe
# recopier le whsec_... affiché dans STRIPE_WEBHOOK_SECRET, puis relancer le backend
stripe trigger checkout.session.completed
```

> **Une seule version d'API dans le projet.** `scripts/stripe-setup.py`,
> `scripts/stripe-webhook-test.py` et `com.stripe.ApiVersion.CURRENT` (figée par la version
> de `stripe-java` dans `pom.xml`) doivent annoncer la même : `2025-04-30.basil`. La
> bibliothèque refuse de désérialiser l'objet d'un événement annoncé dans une autre version,
> et le webhook répond alors « objet non désérialisable » sans rien appliquer — un échec
> silencieux. À revérifier à chaque montée de version de `stripe-java`.

> **Obstacle à connaître.** Stripe ne couvre pas la Côte d'Ivoire : on ne peut pas y ouvrir
> de compte marchand pour une structure ivoirienne (à vérifier sur `stripe.com/global`, la
> liste évolue). Le champ est volontairement nommé `billingCustomerId` et non
> `stripeCustomerId`, et `SubscriptionService` est séparé de `StripeService` : brancher
> CinetPay ou Flutterwave — qui gèrent Orange Money, Wave et MTN — ne demande de réécrire
> que la passerelle, pas la logique d'abonnement.

---

## 12. Mesure d'audience

Page `/platform/audience` : visites, visiteurs distincts, série journalière, histogramme des
pages, répartition des appareils, provenances et entonnoir de candidature. À côté,
`/platform/clients` liste les organisations, leur plan et leur consommation.

**Réservée au compte `ADMIN` de la plateforme**, derrière `adminGuard`. C'est délibérément un
espace à part et non une page de l'espace recruteur : le compte de plateforme n'appartient à
aucune organisation, donc `recruiterGuard` le refusait — et surtout, le trafic de tout le site
n'a pas à être lisible par un client. Côté serveur, `PlatformController` ne sert que des
agrégats et des abonnements : ni candidature, ni CV d'un client.

**Sans cookie et sans adresse IP conservée.** Le visiteur est réduit à une empreinte SHA-256
sur (adresse + navigateur + sel secret + date du jour) :

- *non réversible* : l'adresse IP n'est pas retrouvable depuis ce qui est stocké ;
- *non attaquable par dictionnaire* : sans le sel, on pourrait hacher les quatre milliards
  d'adresses IPv4 et comparer les empreintes. Le sel aléatoire, jamais écrit en base, rend
  ce calcul inutile ;
- *non durable* : le sel inclut la date, donc deux visites du même navigateur à deux jours
  d'écart donnent deux empreintes sans lien.

C'est ce qui permet de se passer de bandeau de consentement : la mesure est strictement
anonyme. Contrepartie assumée — un visiteur qui revient demain est compté comme nouveau.

Les événements de plus de 400 jours sont supprimés chaque nuit
(`AnalyticsService.purgeOldEvents`).

```sql
-- visites par jour
select day, count(*) as visites, count(distinct visitor_hash) as visiteurs
from visit_event where kind = 'PAGE'
group by day order by day desc limit 14;

-- entonnoir de candidature
select kind, count(*) from visit_event group by kind;
```

---

## 13. Avant de prendre le premier euro

- [x] Multi-client avec cloisonnement vérifié
- [x] Quotas et compteur de consommation
- [x] Migrations Flyway versionnées
- [x] Limiteur de débit sur le dépôt public de candidatures
- [x] File d'analyse persistée
- [x] Swagger désactivable (`SWAGGER_ENABLED=false` en production)
- [x] Plan gratuit permanent : aucun compte bloqué par un impayé
- [x] Mesure d'audience sans cookie ni adresse IP conservée
- [ ] **Trancher le prestataire de paiement** : Stripe ne couvre pas la Côte d'Ivoire
- [ ] **Planifier `scripts/backup.sh` par cron, et tester une restauration complète**
- [ ] Copie des sauvegardes hors du serveur
- [x] Paiement par abonnement branché (reste à ouvrir le compte et renseigner les clés)
- [ ] E-mails transactionnels (confirmation candidat, fin d'essai, invitation d'un collègue)
- [ ] Mentions légales, CGV, politique de conservation des CV

---

## 14. Ce qui reste à faire côté produit

- Upload de CV en masse (besoin n° 1 des cabinets, et ce qui justifie le plan Pro).
- Export CSV/PDF du classement.
- Clés d'API et webhooks (plan Business).
- Jeton de rafraîchissement : le jeton dure 8 heures, au-delà il faut se reconnecter.
- Réinitialisation de mot de passe (dépend des e-mails).
- Invitation d'un collègue par e-mail : aujourd'hui le propriétaire choisit un mot de passe
  initial et le transmet lui-même.
