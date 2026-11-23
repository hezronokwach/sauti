-- The running app logs in as a member of sauti_app; Flyway keeps the owner account. The app can
-- use normal tables but cannot change the schema, and the audit tables are append-only for it.
do
$$
    begin
        if not exists (select from pg_roles where rolname = 'sauti_app') then
            create role sauti_app nologin;
        end if;
    end
$$;

revoke create on schema public from public;
grant usage on schema public to sauti_app;

grant select, insert, update, delete on all tables in schema public to sauti_app;
grant usage, select on all sequences in schema public to sauti_app;

-- Tables added by later migrations get the same rights.
alter default privileges in schema public
    grant select, insert, update, delete on tables to sauti_app;
alter default privileges in schema public
    grant usage, select on sequences to sauti_app;

-- The migration history belongs to Flyway alone.
revoke all on flyway_schema_history from sauti_app;

-- Audit rows can be added and read, never changed or removed.
revoke update, delete, truncate on audit_event, access_log from sauti_app;
