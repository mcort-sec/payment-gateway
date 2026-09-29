package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidProcessorCredentialException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class ProcessorCredential {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[a-zA-Z0-9]{8,16}$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    private final UUID id;
    private final UUID processorId;
    private final String keyPrefix;
    private final String keyHash;
    private CredentialStatus status;
    private final Instant createdAt;
    private Instant revokedAt;

    public ProcessorCredential(
            UUID id,
            UUID processorId,
            String keyPrefix,
            String keyHash,
            Instant createdAt
    ) {
        this(id, processorId, keyPrefix, keyHash, CredentialStatus.ACTIVE, createdAt, null);
    }

    private ProcessorCredential(
            UUID id,
            UUID processorId,
            String keyPrefix,
            String keyHash,
            CredentialStatus status,
            Instant createdAt,
            Instant revokedAt
    ) {
        validateInvariants(id, processorId, keyPrefix, keyHash, status, createdAt, revokedAt);

        this.id = id;
        this.processorId = processorId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = status;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public static ProcessorCredential reconstitute(
            UUID id,
            UUID processorId,
            String keyPrefix,
            String keyHash,
            CredentialStatus status,
            Instant createdAt,
            Instant revokedAt
    ) {
        return new ProcessorCredential(id, processorId, keyPrefix, keyHash, status, createdAt, revokedAt);
    }

    public void revoke(Instant at) {
        if (at == null) {
            throw new InvalidProcessorCredentialException("Revocation timestamp cannot be null");
        }
        if (this.status == CredentialStatus.REVOKED) {
            // Idempotent: retain original revocation timestamp
            return;
        }
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = at;
    }

    public boolean isActive() {
        return this.status == CredentialStatus.ACTIVE;
    }

    public boolean isRevoked() {
        return this.status == CredentialStatus.REVOKED;
    }

    private static void validateInvariants(
            UUID id,
            UUID processorId,
            String keyPrefix,
            String keyHash,
            CredentialStatus status,
            Instant createdAt,
            Instant revokedAt
    ) {
        if (id == null) {
            throw new InvalidProcessorCredentialException("Credential ID cannot be null");
        }
        if (processorId == null) {
            throw new InvalidProcessorCredentialException("Processor ID cannot be null");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new InvalidProcessorCredentialException("Key prefix cannot be null or blank");
        }
        if (!PREFIX_PATTERN.matcher(keyPrefix).matches()) {
            throw new InvalidProcessorCredentialException("Key prefix must be 8-16 alphanumeric characters");
        }
        if (keyHash == null || keyHash.isBlank()) {
            throw new InvalidProcessorCredentialException("Key hash cannot be null or blank");
        }
        if (!HASH_PATTERN.matcher(keyHash).matches()) {
            throw new InvalidProcessorCredentialException("Key hash must be a 64-character lowercase hex string");
        }
        if (status == null) {
            throw new InvalidProcessorCredentialException("Credential status cannot be null");
        }
        if (createdAt == null) {
            throw new InvalidProcessorCredentialException("CreatedAt timestamp cannot be null");
        }
        if (status == CredentialStatus.ACTIVE && revokedAt != null) {
            throw new InvalidProcessorCredentialException("Active credential must not have a revokedAt timestamp");
        }
        if (status == CredentialStatus.REVOKED && revokedAt == null) {
            throw new InvalidProcessorCredentialException("Revoked credential must have a revokedAt timestamp");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProcessorId() {
        return processorId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessorCredential that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
