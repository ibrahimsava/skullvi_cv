-- Trois changements : le plan gratuit permanent remplace l'essai, les colonnes de paiement,
-- et la table de mesure d'audience.
--
-- Écrite pour tourner sur la base de production, qui contient déjà des organisations en
-- TRIAL. Aucune suppression de données.


-- ---------------------------------------------------------------- 1. plan gratuit

-- L'essai de 14 jours devient un palier gratuit sans échéance. Les organisations qui
-- étaient en essai ne doivent surtout pas se retrouver bloquées : elles passent en gratuit
-- et gardent l'accès.
update organization set plan = 'FREE' where plan = 'TRIAL';

-- Les statuts se réduisent à ACTIVE / PAST_DUE / CANCELED : le gratuit étant permanent,
-- plus rien ne justifie de suspendre un compte.
update organization set status = 'ACTIVE'    where status in ('TRIAL', 'SUSPENDED');
update organization set status = 'PAST_DUE'  where status = 'PAST_DUE';

-- La date de fin d'essai n'a plus d'objet. On garde la colonne le temps d'une version,
-- pour qu'un retour en arrière du déploiement ne perde pas l'information.
alter table organization alter column trial_ends_at drop not null;

-- ---------------------------------------------------------------- 2. paiement

alter table organization add column if not exists billing_subscription_id varchar(80);
alter table organization add column if not exists current_period_end      timestamp(6) with time zone;
alter table organization add column if not exists cancel_at_period_end    boolean not null default false;

-- Le webhook retrouve l'organisation par son identifiant client : cette recherche doit être
-- indexée, elle a lieu à chaque événement de paiement.
create index if not exists ix_organization_billing_customer
    on organization (billing_customer_id);
create index if not exists ix_organization_billing_subscription
    on organization (billing_subscription_id);

-- ---------------------------------------------------------------- 3. mesure d'audience

create table visit_event (
    id              uuid         primary key,
    organization_id uuid,
    kind            varchar(24)  not null,
    path            varchar(160) not null,
    -- Empreinte quotidienne non réversible. Jamais d'adresse IP ni de cookie : c'est ce qui
    -- permet de mesurer l'audience sans bandeau de consentement.
    visitor_hash    varchar(44)  not null,
    referrer_host   varchar(120),
    device          varchar(16)  not null,
    day             date         not null,
    created_at      timestamp(6) with time zone not null,
    constraint fk_visit_org foreign key (organization_id)
        references organization (id) on delete cascade
);

-- Les trois regroupements de la page de statistiques : série par jour, série par jour pour
-- une organisation, et comptage des visiteurs distincts.
create index ix_visit_day         on visit_event (day);
create index ix_visit_org_day     on visit_event (organization_id, day);
create index ix_visit_visitor_day on visit_event (visitor_hash, day);
create index ix_visit_kind_day    on visit_event (kind, day);
