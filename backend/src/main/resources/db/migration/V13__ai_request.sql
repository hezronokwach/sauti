-- Every call to the clinical AI, successful or not: who asked, for which visit, which model
-- and prompt version, and what came back with the server's checks. Claims and checks quote
-- the transcript, so both are encrypted by the application (AES-256-GCM).
create table ai_request
(
    id             uuid        not null default gen_random_uuid(),
    visit_id       uuid        not null,
    requested_by   uuid        not null,
    kind           varchar(20) not null,
    model          varchar(100) not null,
    prompt_version varchar(50) not null,
    status         varchar(20) not null,
    claims         bytea,
    checks         bytea,
    error          varchar(200),
    duration_ms    integer     not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    version        bigint      not null default 0,
    constraint pk_ai_request primary key (id),
    constraint fk_ai_request_visit foreign key (visit_id) references visit (id),
    constraint fk_ai_request_requested_by foreign key (requested_by) references staff (id),
    constraint ck_ai_request_kind check (kind in ('SUMMARY')),
    constraint ck_ai_request_status check (status in ('OK', 'UNAVAILABLE')),
    constraint ck_ai_request_duration check (duration_ms >= 0)
);

create index ix_ai_request_visit on ai_request (visit_id, created_at);
