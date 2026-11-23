-- One row per nightly backup attempt, for the admin status page. It holds no patient data.
create table backup_run
(
    id          uuid        not null default gen_random_uuid(),
    started_at  timestamptz not null,
    finished_at timestamptz not null,
    status      varchar(10) not null,
    file_name   varchar(100),
    size_bytes  bigint,
    constraint pk_backup_run primary key (id),
    constraint ck_backup_run_status check (status in ('SUCCESS', 'FAILED')),
    constraint ck_backup_run_size check (size_bytes is null or size_bytes > 0),
    constraint ck_backup_run_success_complete
        check (status <> 'SUCCESS' or (file_name is not null and size_bytes is not null))
);

create index ix_backup_run_started on backup_run (started_at desc);
