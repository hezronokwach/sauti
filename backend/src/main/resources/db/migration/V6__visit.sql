-- A patient's visit and its journey through the queues.

create table visit
(
    id                 uuid        not null default gen_random_uuid(),
    patient_id         uuid        not null,
    -- The facility's local day the visit was opened; queue numbers restart each day.
    visit_date         date        not null,
    status             varchar(20) not null,
    -- Encrypted by the application.
    reason_for_visit   bytea,
    queue_number       integer     not null,
    assigned_doctor_id uuid,
    arrived_at         timestamptz not null,
    -- When the current status began: how long the patient has waited in this queue.
    status_changed_at  timestamptz not null,
    opened_at          timestamptz,
    closed_at          timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    version            bigint      not null default 0,
    constraint pk_visit primary key (id),
    constraint fk_visit_patient foreign key (patient_id) references patient (id),
    constraint fk_visit_doctor foreign key (assigned_doctor_id) references staff (id),
    constraint ck_visit_status check (status in ('AT_RECEPTION', 'WAITING_TRIAGE', 'IN_TRIAGE', 'WAITING_DOCTOR',
                                                 'WITH_DOCTOR', 'WAITING_PAYMENT', 'DONE', 'CANCELLED')),
    constraint ck_visit_queue_number check (queue_number > 0),
    constraint ck_visit_closed check ((status in ('DONE', 'CANCELLED')) = (closed_at is not null))
);

create unique index ux_visit_day_queue_number on visit (visit_date, queue_number);
-- A patient has at most one visit in progress.
create unique index ux_visit_one_open_per_patient on visit (patient_id) where status not in ('DONE', 'CANCELLED');
create index ix_visit_status_day on visit (status, visit_date);

-- Every status change, append-only.
create table visit_status_history
(
    id          uuid         not null default gen_random_uuid(),
    visit_id    uuid         not null,
    from_status varchar(20),
    to_status   varchar(20)  not null,
    changed_by  uuid         not null,
    changed_at  timestamptz  not null,
    reason      varchar(200),
    constraint pk_visit_status_history primary key (id),
    constraint fk_visit_status_history_visit foreign key (visit_id) references visit (id),
    constraint fk_visit_status_history_staff foreign key (changed_by) references staff (id)
);

create index ix_visit_status_history_visit on visit_status_history (visit_id, changed_at);

-- Who is looking after the visit at each stage. Created only by the workflow.
create table care_assignment
(
    id         uuid        not null default gen_random_uuid(),
    visit_id   uuid        not null,
    staff_id   uuid        not null,
    role       varchar(20) not null,
    started_at timestamptz not null,
    ended_at   timestamptz,
    constraint pk_care_assignment primary key (id),
    constraint fk_care_assignment_visit foreign key (visit_id) references visit (id),
    constraint fk_care_assignment_staff foreign key (staff_id) references staff (id),
    constraint ck_care_assignment_role check (role in ('RECEPTIONIST', 'NURSE', 'DOCTOR', 'CASHIER')),
    constraint ck_care_assignment_period check (ended_at is null or ended_at >= started_at)
);

create index ix_care_assignment_visit on care_assignment (visit_id);
create index ix_care_assignment_staff on care_assignment (staff_id, ended_at);

-- One row per day; the next queue number comes from an atomic increment of this row.
create table queue_counter
(
    day         date    not null,
    last_number integer not null,
    constraint pk_queue_counter primary key (day),
    constraint ck_queue_counter_positive check (last_number > 0)
);
