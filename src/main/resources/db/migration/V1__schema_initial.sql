-- Schéma tel qu'il existait avant le passage en multi-client, reproduit à l'identique.
--
-- Pourquoi partir de l'ancien schéma plutôt que du nouveau : la base de production a été créée
-- par Hibernate en mode « update » et ne contient aucun historique Flyway. Avec
-- « baseline-on-migrate », Flyway la considère comme déjà à la version 1 et saute ce fichier,
-- pour n'appliquer que V2. Une base vide, elle, exécute V1 puis V2 et arrive exactement au même
-- état. Les deux chemins convergent, sans jamais rejouer une création de table existante.

create table app_user (
    id            uuid         primary key,
    email         varchar(255) not null,
    password_hash varchar(255) not null,
    first_name    varchar(255),
    last_name     varchar(255),
    role          varchar(255) not null,
    enabled       boolean      not null default true,
    created_at    timestamp(6) with time zone,
    constraint uk_app_user_email unique (email)
);

create table candidate (
    id            uuid         primary key,
    first_name    varchar(255),
    last_name     varchar(255),
    email         varchar(255) not null,
    phone         varchar(255),
    linkedin_url  varchar(255),
    github_url    varchar(255),
    portfolio_url varchar(255),
    constraint uk_candidate_email unique (email)
);

create table offer (
    id                   uuid primary key,
    title                varchar(255),
    description          text,
    domain               varchar(255),
    level                varchar(255),
    min_experience_years integer,
    start_date           date,
    closing_date         date,
    status               varchar(255)
);

create table offer_criterion (
    id        uuid    primary key,
    offer_id  uuid    not null,
    name      varchar(255),
    type      varchar(255),
    mandatory boolean not null default false,
    weight    integer not null default 0,
    threshold integer,
    constraint fk_criterion_offer foreign key (offer_id) references offer (id) on delete cascade
);

create table application (
    id             uuid primary key,
    offer_id       uuid not null,
    candidate_id   uuid not null,
    owner_id       uuid,
    status         varchar(255),
    submitted_at   timestamp(6) with time zone,
    cv_path        varchar(255),
    cv_hash        varchar(255),
    extracted_text text,
    profile_json   text,
    score_total    integer,
    priority       varchar(255),
    breakdown_json text,
    failure_reason varchar(500),
    constraint uk_application_offer_candidate unique (offer_id, candidate_id),
    constraint fk_application_offer     foreign key (offer_id)     references offer (id),
    constraint fk_application_candidate foreign key (candidate_id) references candidate (id),
    constraint fk_application_owner     foreign key (owner_id)     references app_user (id)
);

create index ix_application_offer on application (offer_id);
create index ix_application_owner on application (owner_id);
