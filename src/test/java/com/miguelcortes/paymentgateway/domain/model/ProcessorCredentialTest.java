package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidProcessorCredentialException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorCredentialTest {

    private static final String VALID_PREFIX = "aB3xK9pLmN8q";
    private static final String VALID_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    @DisplayName("Should create valid ACTIVE credential with null revokedAt")
    void shouldCreateValidActiveCredential() {
        UUID id = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();

        ProcessorCredential credential = new ProcessorCredential(
                id,
                processorId,
                VALID_PREFIX,
                VALID_HASH,
                FIXED_TIME
        );

        assertEquals(id, credential.getId());
        assertEquals(processorId, credential.getProcessorId());
        assertEquals(VALID_PREFIX, credential.getKeyPrefix());
        assertEquals(VALID_HASH, credential.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, credential.getStatus());
        assertEquals(FIXED_TIME, credential.getCreatedAt());
        assertNull(credential.getRevokedAt());
        assertTrue(credential.isActive());
        assertFalse(credential.isRevoked());
    }

    @Test
    @DisplayName("Should revoke active credential and set revokedAt")
    void shouldRevokeActiveCredential() {
        ProcessorCredential credential = new ProcessorCredential(
                UUID.randomUUID(),
                UUID.randomUUID(),
                VALID_PREFIX,
                VALID_HASH,
                FIXED_TIME
        );

        Instant revocationTime = Instant.parse("2026-09-28T12:00:00Z");
        credential.revoke(revocationTime);

        assertEquals(CredentialStatus.REVOKED, credential.getStatus());
        assertEquals(revocationTime, credential.getRevokedAt());
        assertFalse(credential.isActive());
        assertTrue(credential.isRevoked());
    }

    @Test
    @DisplayName("Should be strictly idempotent when revoking already revoked credential and retain original revokedAt")
    void shouldRetainOriginalRevocationTimeWhenRevokedMultipleTimes() {
        ProcessorCredential credential = new ProcessorCredential(
                UUID.randomUUID(),
                UUID.randomUUID(),
                VALID_PREFIX,
                VALID_HASH,
                FIXED_TIME
        );

        Instant firstRevocation = Instant.parse("2026-09-28T12:00:00Z");
        Instant secondRevocation = Instant.parse("2026-09-28T15:00:00Z");

        credential.revoke(firstRevocation);
        assertEquals(firstRevocation, credential.getRevokedAt());

        credential.revoke(secondRevocation);
        assertEquals(firstRevocation, credential.getRevokedAt(), "Must retain original revocation timestamp");
        assertTrue(credential.isRevoked());
    }

    @Test
    @DisplayName("Should throw exception when revoking with null timestamp")
    void shouldThrowExceptionWhenRevokingWithNullTimestamp() {
        ProcessorCredential credential = new ProcessorCredential(
                UUID.randomUUID(),
                UUID.randomUUID(),
                VALID_PREFIX,
                VALID_HASH,
                FIXED_TIME
        );

        assertThrows(InvalidProcessorCredentialException.class, () -> credential.revoke(null));
    }

    @Test
    @DisplayName("Should reconstitute valid ACTIVE credential")
    void shouldReconstituteActiveCredential() {
        UUID id = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();

        ProcessorCredential credential = ProcessorCredential.reconstitute(
                id,
                processorId,
                VALID_PREFIX,
                VALID_HASH,
                CredentialStatus.ACTIVE,
                FIXED_TIME,
                null
        );

        assertNotNull(credential);
        assertTrue(credential.isActive());
        assertNull(credential.getRevokedAt());
    }

    @Test
    @DisplayName("Should reconstitute valid REVOKED credential")
    void shouldReconstituteRevokedCredential() {
        UUID id = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-09-28T12:00:00Z");

        ProcessorCredential credential = ProcessorCredential.reconstitute(
                id,
                processorId,
                VALID_PREFIX,
                VALID_HASH,
                CredentialStatus.REVOKED,
                FIXED_TIME,
                revokedAt
        );

        assertNotNull(credential);
        assertTrue(credential.isRevoked());
        assertEquals(revokedAt, credential.getRevokedAt());
    }

    @Test
    @DisplayName("Should throw exception when reconstituting ACTIVE credential with non-null revokedAt")
    void shouldThrowExceptionWhenReconstitutingActiveCredentialWithRevokedAt() {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                ProcessorCredential.reconstitute(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        VALID_PREFIX,
                        VALID_HASH,
                        CredentialStatus.ACTIVE,
                        FIXED_TIME,
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("Should throw exception when reconstituting REVOKED credential with null revokedAt")
    void shouldThrowExceptionWhenReconstitutingRevokedCredentialWithNullRevokedAt() {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                ProcessorCredential.reconstitute(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        VALID_PREFIX,
                        VALID_HASH,
                        CredentialStatus.REVOKED,
                        FIXED_TIME,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should throw exception when id is null")
    void shouldThrowExceptionWhenIdIsNull() {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                new ProcessorCredential(null, UUID.randomUUID(), VALID_PREFIX, VALID_HASH, FIXED_TIME)
        );
    }

    @Test
    @DisplayName("Should throw exception when processorId is null")
    void shouldThrowExceptionWhenProcessorIdIsNull() {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                new ProcessorCredential(UUID.randomUUID(), null, VALID_PREFIX, VALID_HASH, FIXED_TIME)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "short", "invalid_prefix!", "tooLongPrefix1234567890"})
    @DisplayName("Should throw exception when keyPrefix is invalid")
    void shouldThrowExceptionWhenKeyPrefixIsInvalid(String invalidPrefix) {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                new ProcessorCredential(UUID.randomUUID(), UUID.randomUUID(), invalidPrefix, VALID_HASH, FIXED_TIME)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "shortHash", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})
    @DisplayName("Should throw exception when keyHash is not 64-char lowercase hex")
    void shouldThrowExceptionWhenKeyHashIsInvalid(String invalidHash) {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                new ProcessorCredential(UUID.randomUUID(), UUID.randomUUID(), VALID_PREFIX, invalidHash, FIXED_TIME)
        );
    }

    @Test
    @DisplayName("Should throw exception when createdAt is null")
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        assertThrows(InvalidProcessorCredentialException.class, () ->
                new ProcessorCredential(UUID.randomUUID(), UUID.randomUUID(), VALID_PREFIX, VALID_HASH, null)
        );
    }
}
