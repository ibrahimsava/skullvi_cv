# Skulvi : notes de développement et aide-mémoire

Stack : front **Angular**, back **Spring Boot 4 (JWT)**, base **PostgreSQL 16**, analyse de CV par **Ollama** (modèle `qwen2.5:3b`), le tout orchestré avec **Docker Compose**.

---

## 1. Architecture Docker

| Service | Rôle | Port |
|---|---|---|
| `postgres` | Base de données | 5433 (hôte) → 5432 |
| `ollama` | Moteur d'IA qui analyse les CV | 11434 |
| `app` (`springboot-app`) | Backend Spring Boot | 8080 |

Volumes : `postgres_data` (base), `cvs` (fichiers PDF, monté sur `/data/cvs`), `ollama_data` (modèles téléchargés).

Points clés du `docker-compose.yml` :

- Le backend parle à Ollama par le **nom du service** : `OLLAMA_URL: http://ollama:11434` (et non `host.docker.internal`).
- Le modèle est choisi avec `OLLAMA_MODEL: qwen2.5:3b`. Il doit être **identique** au modèle téléchargé.
- Le volume `ollama_data` doit être déclaré dans la section `volumes:` en bas du fichier.
- En production, ne pas exposer le port 11434 : Ollama n'a aucune authentification.

---

## 2. Commandes utiles et leur rôle

### Docker

```powershell
docker compose up -d --build      # Reconstruit l'image du backend et relance tout
docker compose up -d              # Relance sans reconstruire (changement de variable d'environnement seulement)
docker compose down               # Arrête et supprime les conteneurs (les volumes sont conservés)
docker ps                         # Liste les conteneurs en cours
docker logs -f springboot-app     # Suit les logs du backend en direct
docker logs --tail 30 springboot-app   # Les 30 dernières lignes de logs
docker logs --tail 20 ollama      # Logs d'Ollama (cherche les lignes POST /api/chat)
docker exec -it springboot-app printenv OLLAMA_MODEL   # Vérifie une variable dans le conteneur
docker exec -it springboot-app ls -la /data/cvs        # Vérifie que les CV sont sur le disque
```

> `--build` est obligatoire dès qu'on modifie du code Java ou `application.yaml` (le fichier est dans le jar). Il n'est pas nécessaire pour un simple changement de variable dans le compose ou le `.env`.

### Ollama (dans Docker)

```powershell
docker exec -it ollama ollama pull qwen2.5:3b                 # Télécharge le modèle (une seule fois, gardé dans le volume)
docker exec -it ollama ollama list                            # Liste les modèles installés
docker exec -it ollama ollama run qwen2.5:3b "Dis bonjour"    # Test rapide du modèle
```

### Diagnostic dans le code

```powershell
# Retrouver les routes d'un contrôleur
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String -Pattern "Mapping\("

# Retrouver la config de sécurité ou le CORS
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String -Pattern "SecurityFilterChain|requestMatchers"
Get-ChildItem src\main\java -Recurse -Filter *.java | Select-String -Pattern "setAllowedMethods|CorsConfiguration"

# Voir la config Ollama / CORS dans application.yaml
Select-String -Path src\main\resources\application* -Pattern "ollama|model|cors"
```

### Front Angular

```powershell
ng serve                       # Lance le front sur http://localhost:4200
ng serve --host 0.0.0.0        # Idem, mais accessible depuis le téléphone (même Wi-Fi)
ipconfig                       # Trouver l'adresse IPv4 du PC (carte Wi-Fi)
```

---

## 3. Problèmes rencontrés et solutions

| Problème | Cause | Solution |
|---|---|---|
| « Service IA indisponible (Ollama) : Connection refused » | Ollama non installé ni lancé, ou inaccessible depuis Docker | Ollama ajouté comme service du `docker-compose.yml`, URL `http://ollama:11434` |
| Analyse toujours en échec alors qu'Ollama répond | Le backend demandait `qwen2.5:7b` mais seul `qwen2.5:3b` était installé | Variable `OLLAMA_MODEL: qwen2.5:3b` et reconstruction |
| Beaucoup de `WARN No Unicode mapping for ... FontAwesome` dans les logs | Les CV contiennent des icônes que PDFBox ne sait pas convertir en texte | Sans gravité, le texte normal est extrait |
| « Impossible d'ouvrir ce CV » (404) | La route `GET /api/v1/applications/{id}/cv` n'existait pas côté backend | Création de `ApplicationCvController` |
| Suppression d'une candidature impossible | Route `DELETE /api/v1/applications/{id}` absente | Ajoutée dans le même contrôleur (supprime aussi le fichier du CV) |
| Tout le fichier `SecurityConfig` en rouge | Ligne `requestMatchers(...)` coupée en plein milieu | Lignes supprimées : `.anyRequest().hasRole("ADMIN")` couvre déjà ces routes |
| Le CV s'ouvrait dans un nouvel onglet | `window.open` dans `viewCv` | Fenêtre modale avec `iframe` et `blob` |
| Connexion impossible depuis le téléphone | Front qui appelait `localhost`, et CORS limité à `localhost` | IP du PC dans `environment.development.ts`, et `CORS_ORIGIN` multi-origines |

---

## 4. Gestion des CV (backend)

Fichier : `application/ApplicationCvController.java`

- `GET /api/v1/applications/{id}/cv` : renvoie le PDF (`?download=true` pour forcer le téléchargement).
- `DELETE /api/v1/applications/{id}` : supprime la candidature et son fichier.
- Le chemin est contrôlé (`startsWith(baseDir)`) pour empêcher de sortir du dossier `CV_STORAGE_DIR`.
- Sécurité : réservé au rôle `ADMIN` par la règle `.anyRequest().hasRole("ADMIN")`.

Côté Angular, le téléchargement passe **obligatoirement par `HttpClient`** avec `responseType: 'blob'`. Un lien direct vers l'URL n'enverrait pas le token JWT et renverrait une erreur 401.

---

## 5. Tester depuis un téléphone (même Wi-Fi)

1. Lancer le front : `ng serve --host 0.0.0.0`.
2. Relever l'IP Wi-Fi du PC avec `ipconfig` (ignorer les adresses WSL ou vEthernet).
3. Front : dans `src/environments/environment.development.ts`
```ts
   apiBaseUrl: 'http://<IP_DU_PC>:8080/api/v1'
```
4. Backend : dans `.env`
```
   CORS_ORIGIN=http://localhost:4200,http://<IP_DU_PC>:4200
```
   `SecurityConfig` doit découper la liste :
```java
   config.setAllowedOrigins(List.of(origin.split("\\s*,\\s*")));
```
5. Reconstruire : `docker compose up -d --build`.
6. Pare-feu Windows (PowerShell en administrateur) :
```powershell
   New-NetFirewallRule -DisplayName "Angular 4200" -Direction Inbound -Protocol TCP -LocalPort 4200 -Action Allow
   New-NetFirewallRule -DisplayName "Backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```
7. Sur le téléphone : tester d'abord `http://<IP_DU_PC>:8080/swagger-ui.html`, puis `http://<IP_DU_PC>:4200`.

Test CORS depuis le PC :
```powershell
curl.exe -i -X OPTIONS http://<IP_DU_PC>:8080/api/v1/auth/login -H "Origin: http://<IP_DU_PC>:4200" -H "Access-Control-Request-Method: POST"
```
Réponse attendue : `200` avec l'en-tête `Access-Control-Allow-Origin`.

> `ng serve` lit `environment.development.ts`, alors que `ng build` lit `environment.ts`. Ne jamais laisser une IP de réseau local dans la configuration de production. Après les tests, remettre `localhost`.

---

## 6. Pièges à retenir

- Une valeur écrite dans `application.yaml` est **dans le jar** : il faut reconstruire l'image (`--build`).
- Le nom du modèle Ollama doit être **exactement** le même côté backend et côté `ollama pull`.
- Le premier appel à Ollama est lent (chargement du modèle). Prévoir un timeout de 120 secondes ou plus côté Spring.
- Sans carte graphique, `qwen2.5:3b` tourne correctement. Le `7b` demande environ 8 Go de RAM et reste lent sur CPU.
- Une analyse déjà en échec ne se relance pas toute seule : utiliser le bouton de relance ou déposer une nouvelle candidature.
- Les CV contiennent des données personnelles : garder l'analyse sur sa propre infrastructure, ne pas exposer le port d'Ollama, et supprimer les fichiers quand une candidature est supprimée.
- Ne jamais pousser sur GitHub le fichier `.env` (secret JWT, mots de passe de la base). Vérifier qu'il figure dans `.gitignore`.

---

## 7. À faire

- Remplacer les chiffres de démonstration de la page d'accueil (250+ offres, 12k+ candidats, 98 %, +38 %) par des valeurs réelles ou les retirer.
- Corriger le lien « Analyse » du menu, qui ouvre actuellement la page de connexion.
- Désactiver Swagger en production (`springdoc.api-docs.enabled=false` et `springdoc.swagger-ui.enabled=false`).
- Remettre `localhost` dans `environment.development.ts` après les tests sur téléphone.
