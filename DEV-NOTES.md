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
| Essai (14 j) | 0 € | 20 | 1 | 1 | 0 |
| Starter | 29 € | 150 | 3 | 2 | 1 |
| Pro | 99 € | 750 | 15 | 10 | 2 |
| Business | 299 € | 3 000 | illimité | illimité | 3 |

Dépassement : **0,20 € par analyse** (`Plan.OVERAGE_CENTS`).

Deux règles à retenir :

- **Un plan payant ne se coupe jamais au quota.** Le dépassement est facturé à l'unité. Couper
  le service d'un client en pleine campagne de recrutement serait absurde.
- **L'essai gratuit s'arrête net.** C'est ce qui le transforme en abonnement.

`QuotaService` est la porte d'entrée unique de toutes ces règles, pour que la question
« ce client a-t-il le droit de faire ça ? » ait toujours la même réponse.

### Encaisser un client (tant que le paiement en ligne n'est pas branché)

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
- **Priorité par plan** : un client qui paie passe devant un essai gratuit quand la file est
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

Deux fichiers dans `src/main/resources/db/migration/` :

- **`V1__schema_initial.sql`** : le schéma *tel qu'il était avant* le multi-client.
- **`V2__multi_client_et_facturation.sql`** : organisations, équipes, quotas, file d'analyse.

Pourquoi partir de l'ancien schéma : la base de production a été créée par Hibernate en mode
`update` et n'a aucun historique Flyway. Avec `baseline-on-migrate: true`, Flyway la considère
comme déjà à la version 1 et saute V1, pour n'appliquer que V2. Une base vide, elle, exécute
V1 puis V2 et arrive au même état. **Les deux chemins convergent**, sans jamais rejouer une
création de table existante.

V2 reprend aussi les données existantes : elle crée une organisation « Skulvi » en plan
`BUSINESS`/`ACTIVE`, y rattache toutes les offres, candidats et candidatures, et fait des
comptes `ADMIN`/`RECRUTEUR` existants ses propriétaires. Un déploiement qui tournait déjà ne se
retrouve donc pas bloqué par un quota du jour au lendemain.

> **Avant de déployer sur la base de production** : faire une sauvegarde
> (`scripts/backup.sh`), puis vérifier dans les logs de démarrage que Flyway affiche
> `Migrating schema "public" to version "2"` et qu'Hibernate ne lève pas de
> `Schema-validation`. Si `validate` échoue, c'est qu'une colonne générée par Hibernate diffère
> de V1 — le corriger par une migration `V3`, jamais à la main.

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
./mvnw test                       # 63 tests backend, sans base de données
cd frontSkulvi; npx ng test --no-watch   # 26 tests front
```

Aucun test n'a besoin de PostgreSQL ni d'Ollama : les règles de quota, la génération des slugs,
le limiteur de débit et les libellés candidat sont testés en isolation. L'ancien
`contextLoads` a été retiré, il exigeait une base et ne vérifiait rien d'utile.

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

## 9. Avant de prendre le premier euro

- [x] Multi-client avec cloisonnement vérifié
- [x] Quotas et compteur de consommation
- [x] Migrations Flyway versionnées
- [x] Limiteur de débit sur le dépôt public de candidatures
- [x] File d'analyse persistée
- [x] Swagger désactivable (`SWAGGER_ENABLED=false` en production)
- [ ] **Planifier `scripts/backup.sh` par cron, et tester une restauration complète**
- [ ] Copie des sauvegardes hors du serveur
- [ ] Paiement en ligne (le point d'accroche est `PlatformController.changePlan`)
- [ ] E-mails transactionnels (confirmation candidat, fin d'essai, invitation d'un collègue)
- [ ] Mentions légales, CGV, politique de conservation des CV

---

## 10. Ce qui reste à faire côté produit

- Upload de CV en masse (besoin n° 1 des cabinets, et ce qui justifie le plan Pro).
- Export CSV/PDF du classement.
- Clés d'API et webhooks (plan Business).
- Jeton de rafraîchissement : le jeton dure 8 heures, au-delà il faut se reconnecter.
- Réinitialisation de mot de passe (dépend des e-mails).
- Invitation d'un collègue par e-mail : aujourd'hui le propriétaire choisit un mot de passe
  initial et le transmet lui-même.
