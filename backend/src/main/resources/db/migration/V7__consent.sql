-- Recording consent decisions, append-only. The latest decision for a visit and purpose
-- is the one that counts, so a withdrawal overrides an earlier agreement.
create table consent
(
    id              uuid        not null default gen_random_uuid(),
    visit_id        uuid        not null,
    purpose         varchar(30) not null,
    decision        varchar(20) not null,
    given_by        varchar(30) not null,
    -- The wording shown, for example v1-sw.
    wording_version varchar(20) not null,
    recorded_by     uuid        not null,
    recorded_at     timestamptz not null,
    constraint pk_consent primary key (id),
    constraint fk_consent_visit foreign key (visit_id) references visit (id),
    constraint fk_consent_recorded_by foreign key (recorded_by) references staff (id),
    constraint ck_consent_purpose check (purpose in ('RECORD_RECEPTION', 'RECORD_CONSULTATION')),
    constraint ck_consent_decision check (decision in ('AGREED', 'REFUSED', 'WITHDRAWN')),
    constraint ck_consent_given_by check (given_by in ('PATIENT', 'PARENT_OR_GUARDIAN', 'REPRESENTATIVE'))
);

create index ix_consent_visit_purpose on consent (visit_id, purpose, recorded_at);
