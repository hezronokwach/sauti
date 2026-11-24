-- The one facility this installation serves. Exactly one row may exist.
-- Columns id, created_at, updated_at and version follow the shared base entity.
create table facility_settings
(
    id         uuid         not null default gen_random_uuid(),
    name       varchar(200) not null,
    code       varchar(20)  not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    version    bigint       not null default 0,
    constraint pk_facility_settings primary key (id),
    constraint ck_facility_settings_name_not_blank check (btrim(name) <> ''),
    constraint ck_facility_settings_code_not_blank check (btrim(code) <> '')
);

-- A unique index on a constant allows at most one row in the table.
create unique index ux_facility_settings_single_row on facility_settings ((true));

-- Demonstration facility; the admin can rename it later.
insert into facility_settings (name, code)
values ('Sauti Demo Clinic', 'SAUTI-DEMO');
