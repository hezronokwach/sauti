-- Safaricom's CheckoutRequestID ends with the payer's phone number, so it is stored encrypted,
-- with a keyed hash for matching callbacks (as patient phone numbers are).
--
-- Plain IDs already stored cannot be encrypted here, because the key lives in the application.
-- They are removed. A prompt still waiting can then never be verified, so it becomes UNCONFIRMED,
-- and the cashier settles it from the M-Pesa statement as for any unconfirmed payment.
update payment
set status = 'UNCONFIRMED'
where method = 'MPESA'
  and status = 'PENDING'
  and checkout_request_id is not null;

alter table payment
    drop constraint uq_payment_checkout_request,
    drop column checkout_request_id,
    add column checkout_request_id   bytea,
    add column checkout_request_hash bytea,
    add constraint uq_payment_checkout_request_hash unique (checkout_request_hash),
    add constraint ck_payment_checkout_request_hash
        check ((checkout_request_id is null) = (checkout_request_hash is null));
