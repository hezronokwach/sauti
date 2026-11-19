-- Everyone who logs in: reception, nurses, doctors, cashiers and admins.
create table staff
(
    id                   uuid         not null default gen_random_uuid(),
    full_name            varchar(200) not null,
    email                varchar(254) not null,
    phone                varchar(20),
    role                 varchar(20)  not null,
    licence_number       varchar(50),
    password_hash        varchar(255) not null,
    -- Authenticator secret, encrypted by the application (mapped in issue 10).
    mfa_secret           bytea,
    mfa_enabled          boolean      not null default false,
    -- Time step of the last accepted authenticator code, so a code works only once.
    last_code_step       bigint,
    failed_logins        integer      not null default 0,
    locked_until         timestamptz,
    status               varchar(20)  not null default 'ACTIVE',
    must_change_password boolean      not null default true,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    version              bigint       not null default 0,
    constraint pk_staff primary key (id),
    constraint ck_staff_role check (role in ('RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER', 'ADMIN')),
    constraint ck_staff_status check (status in ('ACTIVE', 'DISABLED')),
    constraint ck_staff_email_lower check (email = lower(email)),
    constraint ck_staff_full_name_not_blank check (btrim(full_name) <> ''),
    constraint ck_staff_failed_logins check (failed_logins >= 0),
    -- Clinicians must be identifiable by their professional licence.
    constraint ck_staff_clinician_licence check (role not in ('DOCTOR', 'NURSE') or licence_number is not null)
);

-- Emails are stored lower-case, so this also makes them unique regardless of case.
create unique index ux_staff_email on staff (email);
