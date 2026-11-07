-- Passage au multi-client (SaaS) : organisations, équipes, quotas, file d'analyse.
--
-- Cette migration doit fonctionner sur une base vide comme sur la base de production, qui
-- contient déjà des offres et des candidatures. D'où le déroulé : on crée les nouvelles tables,
-- on rattache l'existant à une organisation de reprise, et seulement ensuite on impose le
-- NOT NULL. Ajouter directement une colonne obligatoire à une table peuplée échouerait.

-- ---------------------------------------------------------------- nouvelles tables

create table organization (
    id                   uuid         primary key,
    name                 varchar(255) not null,
    slug                 varchar(60)  not null,
    plan                 varchar(20)  not null,
    status               varchar(20)  not null,
    trial_ends_at        timestamp(6) with time zone,
    billing_customer_id  varchar(80),
    created_at           timestamp(6) with time zone not null,
    constraint uk_organization_slug unique (slug)
);

create table membership (
    id              uuid        primary key,
    organization_id uuid        not null,
    user_id         uuid        not null,
    role            varchar(20) not null,
    created_at      timestamp(6) with time zone not null,
    constraint uk_membership_org_user unique (organization_id, user_id),
    constraint fk_membership_org  foreign key (organization_id) references organization (id) on delete cascade,
    constraint fk_membership_user foreign key (user_id)         references app_user (id)     on delete cascade
);

create table usage_record (
    id              uuid        primary key,
    organization_id uuid        not null,
    period          varchar(7)  not null,
    kind            varchar(30) not null,
    used            integer     not null default 0,
    constraint uk_usage_org_period_kind unique (organization_id, period, kind),
    constraint fk_usage_org foreign key (organization_id) references organization (id) on delete cascade
);

create table analysis_job (
    id              uuid        primary key,
    application_id  uuid        not null,
    organization_id uuid        not null,
    status          varchar(20) not null,
    priority        integer     not null default 0,
    attempts        integer     not null default 0,
    billable        boolean     not null default true,
    created_at      timestamp(6) with time zone not null,
    started_at      timestamp(6) with time zone,
    finished_at     timestamp(6) with time zone,
    last_error      varchar(500)
);

create index ix_job_status_priority on analysis_job (status, priority, created_at);
create index ix_job_application     on analysis_job (application_id);

-- ---------------------------------------------------------------- colonnes de cloisonnement

alter table offer       add column organization_id uuid;
alter table candidate   add column organization_id uuid;
alter table application add column organization_id uuid;

-- ---------------------------------------------------------------- reprise des données existantes

do $$
declare
    legacy_org uuid;
begin
    -- Rien à reprendre sur une base neuve : on n'y crée aucune organisation fantôme.
    if not exists (select 1 from offer)
       and not exists (select 1 from candidate)
       and not exists (select 1 from application) then
        return;
    end if;

    legacy_org := gen_random_uuid();

    -- L'organisation de reprise reçoit le plan le plus large et un statut actif : un déploiement
    -- qui tournait déjà ne doit pas se retrouver bloqué par un quota du jour au lendemain.
    insert into organization (id, name, slug, plan, status, created_at)
    values (legacy_org, 'Skulvi', 'skulvi', 'BUSINESS', 'ACTIVE', now());

    update offer       set organization_id = legacy_org where organization_id is null;
    update candidate   set organization_id = legacy_org where organization_id is null;
    update application set organization_id = legacy_org where organization_id is null;

    -- Les administrateurs existants deviennent propriétaires de cette organisation, pour
    -- garder la main sur les données qu'ils géraient déjà.
    insert into membership (id, organization_id, user_id, role, created_at)
    select gen_random_uuid(), legacy_org, u.id, 'OWNER', now()
    from app_user u
    where u.role in ('ADMIN', 'RECRUTEUR');
end $$;

-- ---------------------------------------------------------------- contraintes définitives

alter table offer       alter column organization_id set not null;
alter table candidate   alter column organization_id set not null;
alter table application alter column organization_id set not null;

alter table offer
    add constraint fk_offer_org foreign key (organization_id) references organization (id);
alter table candidate
    add constraint fk_candidate_org foreign key (organization_id) references organization (id);
alter table application
    add constraint fk_application_org foreign key (organization_id) references organization (id);

-- Un même candidat peut désormais postuler chez deux entreprises différentes : son email n'est
-- unique qu'au sein d'une organisation. L'ancienne contrainte globale l'en empêchait.
-- Le nom varie selon que la table vient de V1 ou d'une génération Hibernate plus ancienne.
do $$
declare
    c record;
begin
    for c in
        select conname
        from pg_constraint
        where conrelid = 'candidate'::regclass
          and contype = 'u'
          and pg_get_constraintdef(oid) = 'UNIQUE (email)'
    loop
        execute format('alter table candidate drop constraint %I', c.conname);
    end loop;
end $$;

alter table candidate
    add constraint uk_candidate_org_email unique (organization_id, email);

create index ix_offer_org_status       on offer (organization_id, status);
create index ix_application_org_status on application (organization_id, status);
