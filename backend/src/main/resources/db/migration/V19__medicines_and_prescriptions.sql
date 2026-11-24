-- The medicine list and prescriptions. The list itself is loaded by the next migration from
-- data/medicines-v1.csv. It is demonstration data and has not been clinically reviewed.
create table allergy_group
(
    code  varchar(40)  not null,
    label varchar(100) not null,
    constraint pk_allergy_group primary key (code)
);

create table medicine
(
    id           uuid          not null default gen_random_uuid(),
    code         varchar(40)   not null,
    name         varchar(120)  not null,
    form         varchar(40)   not null,
    strength     varchar(40)   not null,
    group_code   varchar(40)   not null,
    -- Allowed routes separated by "|", for example ORAL|IV.
    routes       varchar(60)   not null,
    price_kes    numeric(10, 2) not null,
    list_version varchar(30)   not null,
    constraint pk_medicine primary key (id),
    constraint uq_medicine_code unique (code),
    constraint fk_medicine_group foreign key (group_code) references allergy_group (code),
    constraint ck_medicine_price check (price_kes >= 0)
);

create index ix_medicine_name_trgm on medicine using gin (lower(name) gin_trgm_ops);

-- A visit has at most one prescription that is not cancelled. Fixing a signed one means
-- cancelling it and writing a new one.
create table prescription
(
    id            uuid        not null default gen_random_uuid(),
    visit_id      uuid        not null,
    patient_id    uuid        not null,
    prescriber_id uuid        not null,
    status        varchar(20) not null,
    -- The medicine list and rules this prescription was checked against.
    rules_version varchar(30) not null,
    -- Goes up on every item change; the screen sends back the one it read.
    revision      integer     not null default 0,
    signed_at     timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       bigint      not null default 0,
    constraint pk_prescription primary key (id),
    constraint fk_prescription_visit foreign key (visit_id) references visit (id),
    constraint fk_prescription_patient foreign key (patient_id) references patient (id),
    constraint fk_prescription_prescriber foreign key (prescriber_id) references staff (id),
    constraint ck_prescription_status check (status in ('DRAFT', 'SIGNED', 'CANCELLED')),
    constraint ck_prescription_signed check (status <> 'SIGNED' or signed_at is not null)
);

create unique index ux_prescription_one_active on prescription (visit_id) where status <> 'CANCELLED';
create index ix_prescription_patient on prescription (patient_id, created_at);

-- Details may be empty on a draft: the safety rules (issue 42) refuse signing until they are filled.
create table prescription_item
(
    id              uuid        not null default gen_random_uuid(),
    prescription_id uuid        not null,
    medicine_id     uuid        not null,
    dose_value      numeric(10, 3),
    dose_unit       varchar(20),
    frequency       varchar(20),
    duration_days   integer,
    route           varchar(20),
    quantity        integer,
    instructions    bytea,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    constraint pk_prescription_item primary key (id),
    constraint fk_prescription_item_prescription foreign key (prescription_id) references prescription (id),
    constraint fk_prescription_item_medicine foreign key (medicine_id) references medicine (id),
    constraint ck_prescription_item_dose check (dose_value is null or dose_value > 0),
    constraint ck_prescription_item_unit check (dose_unit is null or dose_unit in
        ('MG', 'MCG', 'G', 'ML', 'TABLET', 'CAPSULE', 'PUFF', 'DROP', 'SACHET', 'UNIT', 'APPLICATION')),
    constraint ck_prescription_item_frequency check (frequency is null or frequency in
        ('OD', 'BD', 'TDS', 'QID', 'Q8H', 'STAT', 'NOCTE', 'AS_NEEDED')),
    constraint ck_prescription_item_duration check (duration_days is null or duration_days between 1 and 365),
    constraint ck_prescription_item_quantity check (quantity is null or quantity > 0)
);

create index ix_prescription_item_prescription on prescription_item (prescription_id, created_at);

-- Filled in by the safety rules (issue 42). An alert about the whole prescription has no item.
create table safety_alert
(
    id              uuid        not null default gen_random_uuid(),
    prescription_id uuid        not null,
    item_id         uuid,
    rule            varchar(10) not null,
    level           varchar(20) not null,
    message         bytea       not null,
    status          varchar(20) not null,
    override_reason bytea,
    decided_by      uuid,
    decided_at      timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    constraint pk_safety_alert primary key (id),
    constraint fk_safety_alert_prescription foreign key (prescription_id) references prescription (id),
    constraint fk_safety_alert_item foreign key (item_id) references prescription_item (id) on delete cascade,
    constraint fk_safety_alert_decided_by foreign key (decided_by) references staff (id),
    constraint ck_safety_alert_level check (level in ('BLOCKING', 'REVIEW')),
    constraint ck_safety_alert_status check (status in ('OPEN', 'OVERRIDDEN', 'ACKNOWLEDGED'))
);

create index ix_safety_alert_prescription on safety_alert (prescription_id);
create index ix_safety_alert_item on safety_alert (item_id);
