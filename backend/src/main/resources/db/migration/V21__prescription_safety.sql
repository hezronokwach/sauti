-- Aspirin is an NSAID for allergy purposes: someone allergic to ibuprofen must be warned about it.
-- This correction makes the list version 2.
update medicine
set group_code = 'NSAID', list_version = 'medicines-v2'
where code = 'ASPIR-75-TAB';

alter table prescription
    add column cancelled_at timestamptz,
    add column cancelled_by uuid,
    add constraint fk_prescription_cancelled_by foreign key (cancelled_by) references staff (id),
    add constraint ck_prescription_cancelled check (
        (status = 'CANCELLED') = (cancelled_at is not null and cancelled_by is not null));

-- The database also refuses a decision the rules do not allow: R1 can never be overridden, only
-- blocking alerts are overridden (with a reason), and only review alerts are acknowledged.
alter table safety_alert
    add constraint ck_safety_alert_rule check (rule in ('R1', 'R2', 'R3', 'R4', 'R5', 'R6', 'R7', 'R8')),
    add constraint ck_safety_alert_r1_open check (rule <> 'R1' or status = 'OPEN'),
    add constraint ck_safety_alert_decision check (
        status = 'OPEN'
            or (status = 'OVERRIDDEN' and level = 'BLOCKING' and override_reason is not null
                and decided_by is not null and decided_at is not null)
            or (status = 'ACKNOWLEDGED' and level = 'REVIEW'
                and decided_by is not null and decided_at is not null));
