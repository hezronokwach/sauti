-- Vital signs taken at triage. The ranges are the same plausibility limits the API
-- checks, so an impossible value can never be stored.
create table vitals
(
    id               uuid         not null default gen_random_uuid(),
    visit_id         uuid         not null,
    recorded_by      uuid         not null,
    recorded_at      timestamptz  not null,
    temperature_c    numeric(4, 1) not null,
    pulse_bpm        integer      not null,
    respiratory_rate integer      not null,
    systolic_mmhg    integer      not null,
    diastolic_mmhg   integer      not null,
    spo2_percent     integer      not null,
    weight_kg        numeric(5, 1),
    height_cm        numeric(4, 1),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    constraint pk_vitals primary key (id),
    constraint fk_vitals_visit foreign key (visit_id) references visit (id),
    constraint fk_vitals_recorded_by foreign key (recorded_by) references staff (id),
    constraint ck_vitals_temperature check (temperature_c between 30 and 45),
    constraint ck_vitals_pulse check (pulse_bpm between 20 and 250),
    constraint ck_vitals_respiratory check (respiratory_rate between 4 and 80),
    constraint ck_vitals_systolic check (systolic_mmhg between 50 and 260),
    constraint ck_vitals_diastolic check (diastolic_mmhg between 20 and 160),
    constraint ck_vitals_pressure_order check (systolic_mmhg > diastolic_mmhg),
    constraint ck_vitals_spo2 check (spo2_percent between 50 and 100),
    constraint ck_vitals_weight check (weight_kg is null or weight_kg between 0.5 and 300),
    constraint ck_vitals_height check (height_cm is null or height_cm between 30 and 250)
);

create index ix_vitals_visit on vitals (visit_id, recorded_at);
