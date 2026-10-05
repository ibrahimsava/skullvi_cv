# skullvi_cv

# Skulvi CV

Application Spring Boot (Maven) avec PostgreSQL, lancée via Docker Compose.

## Prérequis

- Docker et Docker Compose
- (Optionnel) Java 21 et Maven pour lancer l'app hors Docker

## Configuration

Copier le fichier d'exemple puis renseigner les valeurs :

```bash
cp .env.example .env
```

**.env.example**
```env
DB_HOST=postgres
DB_PORT=5432
DB_NAME=mydb
DB_USER=myuser
DB_PASSWORD=changeme
DB_EXPOSED_PORT=5433
APP_PORT=8080
```

| Variable          | Rôle                                                              |
|-------------------|-------------------------------------------------------------------|
| `DB_HOST`         | Nom du service Postgres dans le compose (`postgres`)              |
| `DB_PORT`         | Port interne de Postgres (reste `5432`)                           |
| `DB_NAME`         | Nom de la base                                                    |
| `DB_USER`         | Utilisateur de la base                                            |
| `DB_PASSWORD`     | Mot de passe de la base                                           |
| `DB_EXPOSED_PORT` | Port exposé sur la machine hôte (DBeaver, pgAdmin...)             |
| `APP_PORT`        | Port exposé de l'application                                      |

> Le fichier `.env` ne doit **jamais** être commité (il est dans `.gitignore`).

## Lancer le projet

```bash
docker compose up --build
```

- Application : http://localhost:8080 (ou la valeur de `APP_PORT`)
- PostgreSQL depuis l'hôte : `localhost:<DB_EXPOSED_PORT>`

Arrêter :
```bash
docker compose down
```

Arrêter et supprimer les données de la base :
```bash
docker compose down -v
```

## Fonctionnement

- `application.yml` lit la connexion à la base via les variables d'environnement.
- `docker-compose.yml` démarre Postgres avec un healthcheck : l'app ne démarre qu'une fois la base prête.
- Le `Dockerfile` est multi-stage : build avec Maven, exécution avec un JRE léger.

## Dépannage

**`port is already allocated` / `address already in use`**
Un autre service utilise déjà le port. Changer `DB_EXPOSED_PORT` ou `APP_PORT` dans `.env`.
Pour identifier ce qui occupe un port : `sudo lsof -i :5432`

**`TLS handshake timeout` lors du pull des images**
Problème réseau vers Docker Hub. Configurer des DNS publics dans `/etc/docker/daemon.json` :
```json
{ "dns": ["8.8.8.8", "1.1.1.1"] }
```
puis `sudo systemctl restart docker`.

**La configuration datasource n'est pas prise en compte**
Vérifier que `datasource` et `jpa` sont directement sous `spring:` dans `application.yml` (pas de `spring:` imbriqué).

## Structure

```
.
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── pom.xml
└── src/
```