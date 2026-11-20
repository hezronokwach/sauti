-- The latest SHA eligibility answer for a patient, checked in the background once the
-- patient has a national ID. Informational only: it never blocks care.
create table patient_eligibility
(
    id         uuid        not null default gen_random_uuid(),
    patient_id uuid        not null,
    status     varchar(20) not null,
    checked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0,
    constraint pk_patient_eligibility primary key (id),
    constraint uq_patient_eligibility_patient unique (patient_id),
    constraint fk_patient_eligibility_patient foreign key (patient_id) references patient (id),
    constraint ck_patient_eligibility_status check (
        status in ('PENDING', 'ELIGIBLE', 'INELIGIBLE', 'NOT_FOUND', 'UNAVAILABLE')),
    constraint ck_patient_eligibility_checked check ((status = 'PENDING') = (checked_at is null))
);
