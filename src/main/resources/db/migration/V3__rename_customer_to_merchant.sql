ALTER TABLE payments
RENAME COLUMN customer_id TO merchant_id;

ALTER TABLE payments
RENAME CONSTRAINT uq_payments_customer_idempotency
TO uq_payments_merchant_idempotency;
