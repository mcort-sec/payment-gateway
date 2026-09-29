CREATE TABLE processors (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE processor_credentials (
    id UUID PRIMARY KEY,
    processor_id UUID NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_processor_credentials_key_prefix UNIQUE (key_prefix),
    CONSTRAINT fk_processor_credentials_processors
        FOREIGN KEY (processor_id)
        REFERENCES processors(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_processor_credentials_processor_id
ON processor_credentials(processor_id);
