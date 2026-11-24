-- How long registration takes at the desk, for the waiting-time report: started when the
-- patient's ticket was called (or when the visit was opened, without a ticket), completed
-- when the visit was first sent to triage.
alter table visit add column registration_started_at timestamptz;
alter table visit add column registration_completed_at timestamptz;
alter table visit add constraint ck_visit_registration_order
    check (registration_completed_at is null
        or (registration_started_at is not null and registration_completed_at >= registration_started_at));

-- The report looks up each visit's first time in each status.
create index ix_visit_status_history_to on visit_status_history (visit_id, to_status, changed_at);
