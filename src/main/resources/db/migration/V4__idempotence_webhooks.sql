-- Trace des événements de paiement déjà honorés.
--
-- Le prestataire garantit une livraison « au moins une fois » : il réessaie tant qu'il n'a
-- pas reçu un 2xx, et un simple délai réseau suffit à lui faire renvoyer un événement déjà
-- traité. Sans cette table, le même changement d'abonnement serait appliqué deux fois.
--
-- L'identifiant de l'événement est la clé primaire, et c'est tout l'intérêt : la base
-- refuse elle-même le doublon, y compris si deux livraisons arrivent exactement en même
-- temps sur deux threads. Un test applicatif « ai-je déjà vu cet identifiant ? » suivi
-- d'une insertion laisserait au contraire une fenêtre entre les deux.

create table processed_event (
    event_id     varchar(120) primary key,
    event_type   varchar(80),
    processed_at timestamp(6) with time zone not null
);

-- La purge nocturne balaie par date : sans index, elle ferait un parcours complet de la
-- table chaque nuit.
create index ix_processed_event_date on processed_event (processed_at);
