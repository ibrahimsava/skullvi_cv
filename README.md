# Talent Engine : Architecture & UML

> **Principe central** : l'IA comprend les informations non structurées ; le moteur métier décide comment les comparer et les scorer.

Les diagrammes sont en [Mermaid](https://mermaid.js.org/) .

---

## 1. Vue d'ensemble de l'architecture

```mermaid
flowchart LR
    subgraph Client["Frontend : Angular"]
        C1[Espace candidat<br/>offres, formulaire, upload CV]
        C2[Espace recruteur<br/>offres, critères, dashboard, ranking]
    end

    subgraph Backend["Backend : Spring Boot"]
        API[API REST<br/>+ Spring Security]
        APP[Services applicatifs<br/>Offer, Application, Candidate]
        PIPE[Pipeline d'analyse CV]
        MATCH[Matching Engine]
        SCORE[Scoring Engine]
        RANK[Ranking / Priority]
    end

    subgraph Ext["Services externes"]
        LLM[(API LLM)]
    end

    subgraph Data["Données"]
        DB[(PostgreSQL<br/>+ Flyway)]
        FS[(Stockage des CV)]
    end

    C1 --> API
    C2 --> API
    API --> APP
    APP --> PIPE
    PIPE -->|texte du CV| LLM
    LLM -->|JSON structuré| PIPE
    PIPE --> MATCH --> SCORE --> RANK
    APP --> DB
    PIPE --> DB
    MATCH --> DB
    SCORE --> DB
    APP --> FS
```

---

## 2. Séparation IA / moteur métier

```mermaid
flowchart TD
    CV[CV PDF] --> EXT[Extraction du texte<br/>Apache PDFBox / Tika]
    EXT --> CLEAN[Nettoyage du texte]
    CLEAN --> LLM[LLM<br/>extraction structurée en JSON]
    LLM --> VAL{JSON valide ?<br/>preuves présentes dans le CV ?}
    VAL -- non --> FAIL[Statut ANALYSIS_FAILED<br/>candidature conservée, retry possible]
    VAL -- oui --> PROFILE[Profil candidat structuré]
    PROFILE --> NORM[Normalisation<br/>alias, synonymes : Postgres = PostgreSQL]
    NORM --> MATCH[Matching Engine<br/>règles + similarité sémantique]
    OFFER[Critères de l'offre<br/>poids, obligatoire/souhaité] --> MATCH
    MATCH --> SCORE[Scoring Engine<br/>déterministe, testable]
    SCORE --> PRIO[Priorité<br/>seuils configurables]
    PRIO --> RANKING[Classement par offre]

    style LLM fill:#fde68a,stroke:#b45309
    style MATCH fill:#bfdbfe,stroke:#1d4ed8
    style SCORE fill:#bfdbfe,stroke:#1d4ed8
```

> Le LLM n'intervient qu'à une seule étape (extraction). Le score est **calculé en Java**, il est donc reproductible et explicable.

---

## 3. Diagramme de cas d'utilisation

```mermaid
flowchart LR
    Cand((Candidat))
    Rec((Recruteur))
    IA((Service IA))

    subgraph Talent Engine
        UC1[Consulter une offre]
        UC2[Soumettre une candidature]
        UC3[Déposer un CV PDF]
        UC4[Créer une offre]
        UC5[Définir les critères et poids]
        UC6[Consulter les candidatures]
        UC7[Consulter le détail du score]
        UC8[Consulter le classement]
        UC9[Filtrer les candidats]
        UC10[Relancer une analyse échouée]
        UC11[Analyser le CV]
    end

    Cand --> UC1
    Cand --> UC2
    Cand --> UC3
    Rec --> UC4
    Rec --> UC5
    Rec --> UC6
    Rec --> UC7
    Rec --> UC8
    Rec --> UC9
    Rec --> UC10
    UC11 --- IA
    UC3 -.->|déclenche| UC11
```

---

## 4. Modèle de données (ER)

```mermaid
erDiagram
    USER ||--o| CANDIDATE : "est"
    USER ||--o{ OFFER : "crée (recruteur)"
    OFFER ||--o{ OFFER_CRITERION : "définit"
    SKILL ||--o{ OFFER_CRITERION : "référencé par"
    SKILL ||--o{ SKILL_ALIAS : "a pour alias"
    CANDIDATE ||--o{ APPLICATION : "dépose"
    OFFER ||--o{ APPLICATION : "reçoit"
    APPLICATION ||--|| CV_DOCUMENT : "contient"
    APPLICATION ||--o{ CV_ANALYSIS : "analysée par"
    CV_ANALYSIS ||--o{ CANDIDATE_SKILL : "extrait"
    CV_ANALYSIS ||--o{ EXPERIENCE : "extrait"
    CV_ANALYSIS ||--o{ EDUCATION : "extrait"
    CV_ANALYSIS ||--o{ PROJECT : "extrait"
    APPLICATION ||--o{ MATCH_RESULT : "produit"
    OFFER_CRITERION ||--o{ MATCH_RESULT : "évalué dans"
    APPLICATION ||--o| SCORE : "obtient"

    USER {
        uuid id PK
        string email UK
        string password_hash
        string role "CANDIDATE, RECRUITER, ADMIN"
    }
    CANDIDATE {
        uuid id PK
        string first_name
        string last_name
        string email UK
        string phone
        string linkedin_url
        string github_url
        string portfolio_url
    }
    OFFER {
        uuid id PK
        string title
        text description
        string domain
        string level
        int min_experience_years
        date start_date
        date closing_date
        string status "DRAFT, OPEN, CLOSED"
    }
    SKILL {
        uuid id PK
        string name UK
        string category
    }
    SKILL_ALIAS {
        uuid id PK
        uuid skill_id FK
        string alias
    }
    OFFER_CRITERION {
        uuid id PK
        uuid offer_id FK
        uuid skill_id FK
        string category "SKILL, EXPERIENCE, EDUCATION, PROJECT"
        boolean mandatory
        int weight
    }
    APPLICATION {
        uuid id PK
        uuid offer_id FK
        uuid candidate_id FK
        string status
        timestamp submitted_at
    }
    CV_DOCUMENT {
        uuid id PK
        uuid application_id FK
        string storage_path
        string file_hash
        string mime_type
        long size_bytes
    }
    CV_ANALYSIS {
        uuid id PK
        uuid application_id FK
        text extracted_text
        jsonb structured_profile
        string llm_model
        string prompt_version
        string status "SUCCESS, FAILED"
        timestamp analyzed_at
    }
    CANDIDATE_SKILL {
        uuid id PK
        uuid analysis_id FK
        string name
        text evidence
    }
    EXPERIENCE {
        uuid id PK
        uuid analysis_id FK
        string role
        string company
        int duration_months
        text technologies
    }
    EDUCATION {
        uuid id PK
        uuid analysis_id FK
        string degree
        string field
        string institution
    }
    PROJECT {
        uuid id PK
        uuid analysis_id FK
        string title
        text description
    }
    MATCH_RESULT {
        uuid id PK
        uuid application_id FK
        uuid criterion_id FK
        boolean matched
        decimal confidence
        text evidence
        int points_awarded
    }
    SCORE {
        uuid id PK
        uuid application_id FK
        int total
        string priority "HIGH, REVIEW, SECONDARY, LOW"
        jsonb breakdown
        string scoring_version
        timestamp computed_at
    }
```

---

## 5. Diagramme de classes (couche métier)

```mermaid
classDiagram
    class OfferService {
        +create(OfferRequest) Offer
        +close(offerId)
    }
    class ApplicationService {
        +submit(offerId, ApplicationRequest) Application
        +attachCv(applicationId, MultipartFile)
    }
    class CvAnalysisService {
        +analyze(applicationId) CvAnalysis
        -validate(LlmResponse)
    }
    class PdfTextExtractor {
        <<interface>>
        +extract(InputStream) String
    }
    class LlmClient {
        <<interface>>
        +extractProfile(String cvText) StructuredProfile
    }
    class MatchingService {
        +match(StructuredProfile, List~OfferCriterion~) List~MatchResult~
    }
    class SkillNormalizer {
        +normalize(String) String
    }
    class ScoringService {
        +compute(List~MatchResult~, ScoringConfig) Score
    }
    class QualificationService {
        +priorityOf(int score, Thresholds) Priority
    }
    class RankingService {
        +rank(offerId, Filters) List~RankedCandidate~
    }

    ApplicationService --> CvAnalysisService : déclenche
    CvAnalysisService --> PdfTextExtractor
    CvAnalysisService --> LlmClient
    CvAnalysisService --> MatchingService
    MatchingService --> SkillNormalizer
    MatchingService --> ScoringService
    ScoringService --> QualificationService
    RankingService --> ScoringService
    OfferService ..> OfferCriterion
    class OfferCriterion {
        +Skill skill
        +boolean mandatory
        +int weight
    }
```

---

## 6. Séquence : de la candidature au score

```mermaid
sequenceDiagram
    actor Cand as Candidat
    participant UI as Angular
    participant API as Spring Boot API
    participant FS as Stockage CV
    participant AN as CvAnalysisService
    participant LLM as API LLM
    participant M as Matching + Scoring
    participant DB as PostgreSQL

    Cand->>UI: Remplit le formulaire + CV PDF
    UI->>API: POST /offers/{id}/applications
    API->>DB: Crée Candidate + Application (SUBMITTED)
    UI->>API: POST /applications/{id}/cv
    API->>API: Valide type MIME, taille, contenu
    API->>FS: Enregistre le PDF
    API->>DB: Statut CV_UPLOADED
    API-->>UI: 202 Accepted

    Note over API,AN: Traitement asynchrone
    API->>AN: analyze(applicationId)
    AN->>FS: Lit le PDF, extrait le texte
    AN->>LLM: Texte du CV + schéma JSON attendu
    LLM-->>AN: Profil structuré (JSON)
    AN->>AN: Valide le JSON et les preuves
    AN->>DB: Sauvegarde CvAnalysis (ANALYZED)
    AN->>M: match + score
    M->>DB: MatchResult[] + Score (SCORED)
```

---

## 7. Cycle de vie d'une candidature

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED
    SUBMITTED --> CV_UPLOADED : CV valide déposé
    CV_UPLOADED --> ANALYZING : analyse lancée
    ANALYZING --> ANALYZED : profil structuré valide
    ANALYZING --> ANALYSIS_FAILED : extraction / IA / JSON invalide
    ANALYSIS_FAILED --> ANALYZING : relance manuelle ou automatique
    ANALYZED --> SCORED : matching + scoring
    SCORED --> SHORTLISTED : décision du recruteur
    SCORED --> REJECTED : décision du recruteur
    SHORTLISTED --> [*]
    REJECTED --> [*]
```

---

## 8. Scoring et priorisation

Score sur 100, calculé par le backend :

| Catégorie              | Points |
|------------------------|-------:|
| Compétences obligatoires | 40   |
| Compétences souhaitées   | 20   |
| Expérience               | 20   |
| Formation                | 10   |
| Projets                  | 10   |

Formule par catégorie :

```
points_catégorie = max_catégorie × (Σ poids des critères satisfaits / Σ poids des critères de la catégorie)
score_final      = Σ points_catégorie
```

| Score    | Priorité                 |
|----------|--------------------------|
| 80 – 100 | Élevée                   |
| 60 – 79  | À examiner               |
| 40 – 59  | Secondaire               |
| 0 – 39   | Faible correspondance    |

Seuils et pondérations sont stockés en configuration (par offre ou globalement).

---

## 9. Structure du projet Spring Boot

```
src/main/java/org/example/skulvi_cv/
├── config/            # Security, OpenAPI, propriétés (seuils, LLM)
├── offer/             # controller, service, repository, dto, entity
├── candidate/
├── application/
├── cv/                # upload, stockage, extraction PDF
├── analysis/          # CvAnalysisService, LlmClient, prompts, validation
├── matching/          # MatchingService, SkillNormalizer
├── scoring/           # ScoringService, QualificationService
├── ranking/           # RankingService, filtres
├── dashboard/         # statistiques
└── common/            # exceptions, handler global, utilitaires
```

Organisation **par fonctionnalité** (et non par couche technique) : chaque package est cohérent et facilement testable.

---

## 10. Pistes d'amélioration du cahier des charges

**Cohérence**
- Les sections 6 et 10 décrivent deux grilles de poids différentes (critère par critère, puis par catégorie). Retenir une règle unique : les poids par critère s'agrègent par catégorie (voir formule section 8).
- Harmoniser le nom de l'organisation (`SKULLVI` / `Skulvi`).

**Robustesse**
- **Traitement asynchrone** de l'analyse (`@Async` ou file de tâches) : l'upload répond en `202` sans attendre le LLM.
- **Retry** avec backoff et statut `ANALYSIS_FAILED` explicite.
- **Idempotence** : hash du fichier pour détecter les doublons, contrainte unique `(candidate, offer)`.
- **Validation du JSON** du LLM (schéma strict, valeurs inconnues rejetées).

**Fiabilité de l'IA**
- Demander au LLM une **sortie JSON contrainte** avec, pour chaque compétence, l'extrait du CV qui la justifie (`evidence`).
- **Vérifier côté backend** que cet extrait existe réellement dans le texte du CV : cela limite les hallucinations.
- **Normalisation avant le LLM** : table `SKILL` + `SKILL_ALIAS` (Postgres = PostgreSQL) qui résout déjà une grande partie des cas sémantiques sans appel IA.
- Versionner le prompt (`prompt_version`) et le modèle utilisé dans `CV_ANALYSIS`.

**Scoring**
- Définir la règle des critères **obligatoires** : pénalisation, plafonnement du score, ou simple signalement ? À trancher et documenter.
- Stocker `scoring_version` et un snapshot du détail (`breakdown`) pour que le score reste explicable même si l'offre est modifiée ensuite.
- Gérer le cas « offre sans critères » (score non calculable, pas de division par zéro).

**Éthique et conformité**
- Ne **jamais** utiliser nom, âge, genre, photo ou nationalité dans le scoring : les exclure explicitement du prompt et du matching.
- Garder l'humain dans la boucle : statuts `SHORTLISTED` / `REJECTED` décidés par le recruteur uniquement.
- Prévoir suppression/anonymisation des données (RGPD) et durée de conservation des CV.

**Sécurité**
- Vérifier le type réel du fichier (signature PDF, pas seulement l'extension), limiter la taille, renommer les fichiers à l'enregistrement.
- Rate limiting sur l'endpoint de candidature publique.
- Ne jamais commiter la clé de l'API LLM (variable d'environnement).

**Qualité**
- Tests du moteur de scoring avec des **jeux de profils fixes** (golden tests) : même entrée, même score.
- Mock du `LlmClient` dans les tests d'intégration pour ne pas dépendre du réseau.
- Migrations **Flyway** dès le départ, `ddl-auto: validate` en production (au lieu de `update`).

**Priorisation du MVP**
1. Offre + critères + candidature + upload
2. Extraction PDF + appel LLM + profil structuré
3. Matching + scoring + explication
4. Ranking
5. Dashboard et filtres (en dernier, si le temps le permet)

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
## Notes de développement
Commandes utiles, problèmes résolus et aide-mémoire : voir [DEV-NOTES.md](DEV-NOTES.md).

