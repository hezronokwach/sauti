-- The bill for a visit and the payments against it. Prices are copied onto the lines, so a later
-- price change never alters an issued invoice.
create table invoice
(
    id         uuid           not null,
    visit_id   uuid           not null,
    patient_id uuid           not null,
    status     varchar(20)    not null,
    total_kes  numeric(12, 2) not null,
    created_by uuid           not null,
    created_at timestamptz    not null default now(),
    updated_at timestamptz    not null default now(),
    version    bigint         not null default 0,
    constraint pk_invoice primary key (id),
    constraint uq_invoice_visit unique (visit_id),
    constraint fk_invoice_visit foreign key (visit_id) references visit (id),
    constraint fk_invoice_patient foreign key (patient_id) references patient (id),
    constraint fk_invoice_created_by foreign key (created_by) references staff (id),
    constraint ck_invoice_status check (status in ('UNPAID', 'PAID', 'INSURANCE_PENDING')),
    constraint ck_invoice_total check (total_kes >= 0)
);

create table invoice_line
(
    id             uuid           not null,
    invoice_id     uuid           not null,
    position       integer        not null,
    kind           varchar(20)    not null,
    description    bytea          not null,
    quantity       integer        not null,
    unit_price_kes numeric(10, 2) not null,
    amount_kes     numeric(12, 2) not null,
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now(),
    version        bigint         not null default 0,
    constraint pk_invoice_line primary key (id),
    constraint uq_invoice_line_position unique (invoice_id, position),
    constraint fk_invoice_line_invoice foreign key (invoice_id) references invoice (id),
    constraint ck_invoice_line_kind check (kind in ('CONSULTATION', 'MEDICINE')),
    constraint ck_invoice_line_quantity check (quantity > 0),
    constraint ck_invoice_line_price check (unit_price_kes >= 0),
    constraint ck_invoice_line_amount check (amount_kes = quantity * unit_price_kes)
);

create table payment
(
    id                  uuid           not null,
    invoice_id          uuid           not null,
    method              varchar(20)    not null,
    amount_kes          numeric(12, 2) not null,
    status              varchar(20)    not null,
    idempotency_key     varchar(64)    not null,
    checkout_request_id varchar(100),
    mpesa_receipt       varchar(40),
    recorded_by         uuid           not null,
    created_at          timestamptz    not null default now(),
    updated_at          timestamptz    not null default now(),
    version             bigint         not null default 0,
    constraint pk_payment primary key (id),
    constraint uq_payment_idempotency_key unique (idempotency_key),
    constraint uq_payment_checkout_request unique (checkout_request_id),
    constraint uq_payment_mpesa_receipt unique (mpesa_receipt),
    constraint fk_payment_invoice foreign key (invoice_id) references invoice (id),
    constraint fk_payment_recorded_by foreign key (recorded_by) references staff (id),
    constraint ck_payment_method check (method in ('CASH', 'INSURANCE', 'MPESA')),
    constraint ck_payment_status check (status in ('PENDING', 'PAID', 'FAILED', 'UNCONFIRMED')),
    constraint ck_payment_amount check (amount_kes >= 0)
);

-- An invoice can never be paid twice: only a failed attempt leaves room for another.
create unique index uq_payment_one_live_per_invoice on payment (invoice_id) where status <> 'FAILED';
