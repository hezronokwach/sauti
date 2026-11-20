-- Patients and their allergies.
-- Identifiers (phone, national ID, SHA number, next of kin phone) are encrypted by the
-- application; the *_hash columns are keyed hashes of the normalised values, so exact
-- lookups and duplicate checks work without decrypting. Names stay plain for search.

create extension if not exists pg_trgm;

create sequence patient_number_seq start with 1 increment by 1;

create table patient
(
    id                 uuid         not null default gen_random_uuid(),
    -- Shown as SH-000123.
    patient_number     bigint       not null default nextval('patient_number_seq'),
    full_name          varchar(200) not null,
    date_of_birth      date         not null,
    sex                varchar(10)  not null,
    phone              bytea,
    phone_hash         bytea,
    national_id        bytea,
    national_id_hash   bytea,
    sha_number         bytea,
    residence          varchar(200),
    next_of_kin_name   varchar(200),
    next_of_kin_phone  bytea,
    preferred_language varchar(10)  not null default 'SWAHILI',
    payer_type         varchar(10)  not null default 'CASH',
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    version            bigint       not null default 0,
    constraint pk_patient primary key (id),
    constraint ux_patient_number unique (patient_number),
    constraint ck_patient_full_name_not_blank check (btrim(full_name) <> ''),
    constraint ck_patient_sex check (sex in ('FEMALE', 'MALE', 'INTERSEX')),
    constraint ck_patient_language check (preferred_language in ('SWAHILI', 'ENGLISH')),
    constraint ck_patient_payer check (payer_type in ('CASH', 'SHA', 'INSURANCE')),
    -- A hash always travels with its value.
    constraint ck_patient_phone_hash check ((phone is null) = (phone_hash is null)),
    constraint ck_patient_national_id_hash check ((national_id is null) = (national_id_hash is null))
);

alter sequence patient_number_seq owned by patient.patient_number;

-- One person per national ID; phones are shared within families, so not unique.
create unique index ux_patient_national_id_hash on patient (national_id_hash) where national_id_hash is not null;
create index ix_patient_phone_hash on patient (phone_hash) where phone_hash is not null;
-- "achi" finds "Achieng": contains-search on the lower-cased name.
create index ix_patient_name_trgm on patient using gin (lower(full_name) gin_trgm_ops);

create table patient_allergy
(
    id                       uuid         not null default gen_random_uuid(),
    patient_id               uuid         not null,
    -- NO_KNOWN_ALLERGIES is its own entry, so "asked, none" differs from "never asked".
    kind                     varchar(20)  not null,
    substance                varchar(100),
    substance_group          varchar(100),
    reaction                 varchar(200),
    status                   varchar(20)  not null default 'ACTIVE',
    recorded_by              uuid         not null,
    entered_in_error_reason  varchar(200),
    entered_in_error_by      uuid,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    version                  bigint       not null default 0,
    constraint pk_patient_allergy primary key (id),
    constraint fk_patient_allergy_patient foreign key (patient_id) references patient (id),
    constraint fk_patient_allergy_recorded_by foreign key (recorded_by) references staff (id),
    constraint fk_patient_allergy_error_by foreign key (entered_in_error_by) references staff (id),
    constraint ck_patient_allergy_kind check (kind in ('ALLERGY', 'NO_KNOWN_ALLERGIES')),
    constraint ck_patient_allergy_status check (status in ('ACTIVE', 'ENTERED_IN_ERROR')),
    constraint ck_patient_allergy_substance check (
        (kind = 'ALLERGY' and substance is not null and btrim(substance) <> '')
        or (kind = 'NO_KNOWN_ALLERGIES' and substance is null)),
    constraint ck_patient_allergy_error_reason check (
        status = 'ACTIVE' or (entered_in_error_reason is not null and entered_in_error_by is not null))
);

create index ix_patient_allergy_patient on patient_allergy (patient_id);
-- At most one active "no known allergies" per patient.
create unique index ux_patient_allergy_one_nka on patient_allergy (patient_id)
    where kind = 'NO_KNOWN_ALLERGIES' and status = 'ACTIVE';
