-- Arrival tickets. A ticket has a code such as N-007 (lane letter and the day's number in
-- that lane); the waiting-area display shows codes only, never names.
create table queue_ticket
(
    id          uuid        not null default gen_random_uuid(),
    ticket_date date        not null,
    lane        varchar(20) not null,
    number      integer     not null,
    code        varchar(10) not null,
    status      varchar(20) not null,
    arrived_at  timestamptz not null,
    issued_by   uuid        not null,
    called_at   timestamptz,
    called_by   uuid,
    visit_id    uuid,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     bigint      not null default 0,
    constraint pk_queue_ticket primary key (id),
    constraint uq_queue_ticket_code unique (ticket_date, code),
    constraint uq_queue_ticket_visit unique (visit_id),
    constraint fk_queue_ticket_issued_by foreign key (issued_by) references staff (id),
    constraint fk_queue_ticket_called_by foreign key (called_by) references staff (id),
    constraint fk_queue_ticket_visit foreign key (visit_id) references visit (id),
    constraint ck_queue_ticket_lane check (lane in ('PRE_REGISTERED', 'RETURNING', 'NEW', 'EMERGENCY')),
    constraint ck_queue_ticket_status check (status in ('WAITING', 'CALLED', 'SERVED', 'CANCELLED')),
    constraint ck_queue_ticket_number check (number > 0),
    constraint ck_queue_ticket_called check ((called_at is null) = (called_by is null)),
    constraint ck_queue_ticket_served check ((status = 'SERVED') = (visit_id is not null))
);

-- The call-next search: today's waiting tickets in arrival order.
create index ix_queue_ticket_waiting on queue_ticket (ticket_date, status, arrived_at);

create table queue_ticket_counter
(
    day         date        not null,
    lane        varchar(20) not null,
    last_number integer     not null,
    constraint pk_queue_ticket_counter primary key (day, lane),
    constraint ck_queue_ticket_counter_positive check (last_number > 0)
);
