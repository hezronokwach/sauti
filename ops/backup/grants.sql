-- Restores the app's database rights after a restore. Keep in step with V24__app_database_role.sql.
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
alter default privileges in schema public grant select, insert, update, delete on tables to sauti_app;
alter default privileges in schema public grant usage, select on sequences to sauti_app;
revoke all on flyway_schema_history from sauti_app;
revoke update, delete, truncate on audit_event, access_log from sauti_app;
