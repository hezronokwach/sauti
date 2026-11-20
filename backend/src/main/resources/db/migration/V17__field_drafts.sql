-- Voice drafts for free-text reception fields. A piece recorded while reception was on a
-- field carries that field's name; after transcription the model proposes a value with the
-- transcript words it rests on. Drafts are stored apart and never enter the patient record
-- until a person confirms them through the normal form.
alter table audio_piece add column field_tag varchar(30);
alter table audio_piece add constraint ck_audio_piece_field_tag
    check (field_tag is null or field_tag in ('REASON_FOR_VISIT', 'RESIDENCE', 'NEXT_OF_KIN_NAME'));

create table field_draft
(
    id         uuid        not null default gen_random_uuid(),
    visit_id   uuid        not null,
    session_id uuid        not null,
    piece_id   uuid        not null,
    field      varchar(30) not null,
    value      bytea       not null,
    quote      bytea       not null,
    start_ms   integer     not null,
    status     varchar(20) not null,
    decided_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0,
    constraint pk_field_draft primary key (id),
    constraint fk_field_draft_visit foreign key (visit_id) references visit (id),
    constraint fk_field_draft_session foreign key (session_id) references capture_session (id),
    constraint fk_field_draft_piece foreign key (piece_id) references audio_piece (id),
    constraint fk_field_draft_decided_by foreign key (decided_by) references staff (id),
    constraint ck_field_draft_field check (field in ('REASON_FOR_VISIT', 'RESIDENCE', 'NEXT_OF_KIN_NAME')),
    constraint ck_field_draft_status check (status in ('PENDING', 'CONFIRMED', 'DISMISSED')),
    constraint ck_field_draft_decided check ((status = 'PENDING') = (decided_by is null))
);

create index ix_field_draft_visit on field_draft (visit_id, status);
create index ix_field_draft_piece on field_draft (piece_id);

-- Field drafts are recorded like summaries.
alter table ai_request drop constraint ck_ai_request_kind;
alter table ai_request add constraint ck_ai_request_kind check (kind in ('SUMMARY', 'FIELD_DRAFT'));

-- The nurse confirms the reason for visit at triage.
alter table visit add column reason_confirmed_by uuid;
alter table visit add column reason_confirmed_at timestamptz;
alter table visit add constraint fk_visit_reason_confirmed_by foreign key (reason_confirmed_by) references staff (id);
alter table visit add constraint ck_visit_reason_confirmed
    check ((reason_confirmed_by is null) = (reason_confirmed_at is null));
