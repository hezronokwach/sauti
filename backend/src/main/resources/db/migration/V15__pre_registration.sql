-- Pre-registration from the public page: what the patient typed before reaching the desk.
-- UNVERIFIED until reception checks it with the patient; names, phone and reason are
-- encrypted by the application. Every entry is deleted at the end of its day.
create table pre_registration
(
    id                 uuid        not null default gen_random_uuid(),
    ticket_id          uuid        not null,
    ticket_date        date        not null,
    full_name          bytea       not null,
    date_of_birth      date        not null,
    sex                varchar(10) not null,
    phone              bytea       not null,
    phone_hash         bytea       not null,
    reason_for_visit   bytea,
    preferred_language varchar(10) not null,
    -- For rate limiting only; the address itself is never stored.
    ip_hash            bytea       not null,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    version            bigint      not null default 0,
    constraint pk_pre_registration primary key (id),
    constraint uq_pre_registration_ticket unique (ticket_id),
    constraint fk_pre_registration_ticket foreign key (ticket_id) references queue_ticket (id) on delete cascade,
    constraint ck_pre_registration_sex check (sex in ('FEMALE', 'MALE', 'INTERSEX')),
    constraint ck_pre_registration_language check (preferred_language in ('SWAHILI', 'ENGLISH'))
);

create index ix_pre_registration_phone on pre_registration (phone_hash, ticket_date);
create index ix_pre_registration_ip on pre_registration (ip_hash, created_at);

-- Tickets from the public page have no staff member issuing them.
alter table queue_ticket alter column issued_by drop not null;
