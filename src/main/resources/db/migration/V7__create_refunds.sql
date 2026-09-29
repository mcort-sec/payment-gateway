CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_refunds_amount CHECK (amount > 0),
    CONSTRAINT uq_refunds_merchant_idempotency UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT fk_refunds_payments
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_merchants
        FOREIGN KEY (merchant_id)
        REFERENCES merchants(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_refunds_payment_id
ON refunds(payment_id);
