-- The doctor's note for a visit: one per visit, drafted freely, then signed and never changed.
-- The sections are encrypted by the application (AES-256-GCM), so the database holds only
-- ciphertext. Corrections after signing are addenda, which are never changed either.
create table clinical_note
(
    id                   uuid        not null default gen_random_uuid(),
    visit_id             uuid        not null,
    patient_id           uuid        not null,
    author_id            uuid        not null,
    status               varchar(20) not null,
    presenting_complaint bytea,
    history              bytea,
    examination          bytea,
    assessment           bytea,
    plan                 bytea,
    ai_assisted          boolean     not null default false,
    signed_at            timestamptz,
    signed_by            uuid,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    version              bigint      not null default 0,
    constraint pk_clinical_note primary key (id),
    constraint uq_clinical_note_visit unique (visit_id),
    constraint fk_clinical_note_visit foreign key (visit_id) references visit (id),
    constraint fk_clinical_note_patient foreign key (patient_id) references patient (id),
    constraint fk_clinical_note_author foreign key (author_id) references staff (id),
    constraint fk_clinical_note_signed_by foreign key (signed_by) references staff (id),
    constraint ck_clinical_note_status check (status in ('DRAFT', 'SIGNED')),
    constraint ck_clinical_note_signed check (
        (status = 'SIGNED') = (signed_at is not null and signed_by is not null)
            and (status = 'DRAFT' or (assessment is not null and plan is not null)))
);

create index ix_clinical_note_patient on clinical_note (patient_id, signed_at);

create table note_addendum
(
    id         uuid        not null default gen_random_uuid(),
    note_id    uuid        not null,
    author_id  uuid        not null,
    body       bytea       not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0,
    constraint pk_note_addendum primary key (id),
    constraint fk_note_addendum_note foreign key (note_id) references clinical_note (id),
    constraint fk_note_addendum_author foreign key (author_id) references staff (id)
);

create index ix_note_addendum_note on note_addendum (note_id, created_at);
