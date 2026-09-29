package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorCredentialEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessorCredentialMapperTest {

    private ProcessorCredentialMapper mapper;

    private static final String PREFIX = "pB3xK9pLmN8q";
    private static final String HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new ProcessorCredentialMapper();
    }

    @Test
    @DisplayName("Should map ACTIVE domain model to entity and back")
    void shouldMapActiveDomainToEntityAndBack() {
        UUID id = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();

        ProcessorCredential domain = new ProcessorCredential(id, processorId, PREFIX, HASH, CREATED_AT);

        ProcessorCredentialEntity entity = mapper.toEntity(domain);
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(processorId, entity.getProcessorId());
        assertEquals(PREFIX, entity.getKeyPrefix());
        assertEquals(HASH, entity.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, entity.getStatus());
        assertEquals(CREATED_AT, entity.getCreatedAt());
        assertNull(entity.getRevokedAt());

        ProcessorCredential mappedBack = mapper.toDomain(entity);
        assertNotNull(mappedBack);
        assertEquals(id, mappedBack.getId());
        assertEquals(processorId, mappedBack.getProcessorId());
        assertEquals(PREFIX, mappedBack.getKeyPrefix());
        assertEquals(HASH, mappedBack.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, mappedBack.getStatus());
        assertEquals(CREATED_AT, mappedBack.getCreatedAt());
        assertNull(mappedBack.getRevokedAt());
    }

    @Test
    @DisplayName("Should map REVOKED domain model to entity and back")
    void shouldMapRevokedDomainToEntityAndBack() {
        UUID id = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-09-28T12:00:00Z");

        ProcessorCredential domain = ProcessorCredential.reconstitute(
                id,
                processorId,
                PREFIX,
                HASH,
                CredentialStatus.REVOKED,
                CREATED_AT,
                revokedAt
        );

        ProcessorCredentialEntity entity = mapper.toEntity(domain);
        assertNotNull(entity);
        assertEquals(CredentialStatus.REVOKED, entity.getStatus());
        assertEquals(revokedAt, entity.getRevokedAt());

        ProcessorCredential mappedBack = mapper.toDomain(entity);
        assertNotNull(mappedBack);
        assertEquals(CredentialStatus.REVOKED, mappedBack.getStatus());
        assertEquals(revokedAt, mappedBack.getRevokedAt());
    }

    @Test
    @DisplayName("Should return null when mapping null domain or entity")
    void shouldReturnNullForNullInput() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }
}
