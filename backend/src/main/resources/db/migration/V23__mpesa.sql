-- What M-Pesa verification needs on a payment. The callback's amount and receipt are only claims
-- until the status query confirms the payment, so they are kept apart from mpesa_receipt.
alter table payment
    add column prompted_at            timestamptz,
    add column callback_at            timestamptz,
    add column callback_amount_kes    numeric(12, 2),
    add column callback_receipt       varchar(40),
    add column verification_queued_at timestamptz,
    add column resolution_reason      bytea,
    add column resolved_by            uuid,
    add column resolved_at            timestamptz,
    add constraint fk_payment_resolved_by foreign key (resolved_by) references staff (id),
    add constraint ck_payment_mpesa_paid_has_receipt
        check (method <> 'MPESA' or status <> 'PAID' or mpesa_receipt is not null),
    add constraint ck_payment_resolution_complete
        check ((resolved_at is null) = (resolved_by is null)
            and (resolved_at is null) = (resolution_reason is null));

-- The sweep looks for prompts that never had a callback.
create index ix_payment_pending_unqueued on payment (prompted_at)
    where status = 'PENDING' and method = 'MPESA' and verification_queued_at is null;
