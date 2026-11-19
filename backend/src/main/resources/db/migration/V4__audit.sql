-- Append-only history of security and record events. The application only inserts
-- and reads these tables; phase 8 removes update and delete rights from its database
-- user. Rows are never changed, so there are no updated_at or version columns.
create table audit_event
(
    id          uuid        not null,
    occurred_at timestamptz not null,
    -- Empty for events without a logged-in person, such as a failed login.
    actor_id    uuid,
    actor_role  varchar(20),
    action      varchar(50) not null,
    entity_type varchar(50),
    entity_id   uuid,
    -- Patient and visit tables arrive in phase 2; these stay plain indexed columns.
    patient_id  uuid,
    visit_id    uuid,
    request_id  varchar(36),
    ip          varchar(45),
    -- A short note as JSON, never clinical text.
    detail      jsonb,
    constraint pk_audit_event primary key (id),
    constraint fk_audit_event_actor foreign key (actor_id) references staff (id),
    constraint ck_audit_event_detail_size check (detail is null or octet_length(detail::text) <= 2000)
);

create index ix_audit_event_occurred_at on audit_event (occurred_at);
create index ix_audit_event_actor on audit_event (actor_id, occurred_at);
create index ix_audit_event_patient on audit_event (patient_id, occurred_at);
create index ix_audit_event_entity on audit_event (entity_type, entity_id);

-- Who opened which patient's clinical data, and when.
create table access_log
(
    id          uuid        not null,
    occurred_at timestamptz not null,
    actor_id    uuid        not null,
    patient_id  uuid,
    visit_id    uuid,
    what        varchar(50) not null,
    constraint pk_access_log primary key (id),
    constraint fk_access_log_actor foreign key (actor_id) references staff (id)
);

create index ix_access_log_patient on access_log (patient_id, occurred_at);
create index ix_access_log_actor on access_log (actor_id, occurred_at);
