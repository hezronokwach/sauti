-- Background jobs. Workers claim queued jobs with "for update skip locked", so two
-- workers never take the same job; every handler must be safe to run twice.
create table job
(
    id           uuid         not null default gen_random_uuid(),
    type         varchar(40)  not null,
    -- The record the job works on, for example an audio piece.
    payload_id   uuid         not null,
    status       varchar(20)  not null default 'QUEUED',
    attempts     integer      not null default 0,
    run_after    timestamptz  not null,
    locked_until timestamptz,
    last_error   varchar(500),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint pk_job primary key (id),
    constraint ck_job_status check (status in ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED')),
    constraint ck_job_attempts check (attempts >= 0)
);

-- The claim query: queued jobs that are due, oldest first.
create index ix_job_queued on job (run_after) where status = 'QUEUED';
-- The recovery query: running jobs whose lock has expired.
create index ix_job_running on job (locked_until) where status = 'RUNNING';
create index ix_job_payload on job (type, payload_id);
