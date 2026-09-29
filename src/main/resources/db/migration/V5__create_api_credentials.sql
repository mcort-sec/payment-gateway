CREATE TABLE api_credentials (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_api_credentials_key_prefix UNIQUE (key_prefix),
    CONSTRAINT fk_api_credentials_merchants
        FOREIGN KEY (merchant_id)
        REFERENCES merchants(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_api_credentials_merchant_id
ON api_credentials(merchant_id);
