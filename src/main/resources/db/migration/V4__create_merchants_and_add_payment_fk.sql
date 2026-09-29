CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_merchants_email UNIQUE (email)
);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_merchants
    FOREIGN KEY (merchant_id)
    REFERENCES merchants(id)
    ON DELETE RESTRICT;
