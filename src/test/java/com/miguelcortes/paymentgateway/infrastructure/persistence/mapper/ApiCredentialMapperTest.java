package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ApiCredentialEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiCredentialMapperTest {

    private ApiCredentialMapper mapper;

    private static final String PREFIX = "aB3xK9pLmN8q";
    private static final String HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final Instant CREATED_AT = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new ApiCredentialMapper();
    }

    @Test
    @DisplayName("Should map ACTIVE domain model to entity and back")
    void shouldMapActiveDomainToEntityAndBack() {
        UUID id = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        ApiCredential domain = new ApiCredential(id, merchantId, PREFIX, HASH, CREATED_AT);

        ApiCredentialEntity entity = mapper.toEntity(domain);
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(merchantId, entity.getMerchantId());
        assertEquals(PREFIX, entity.getKeyPrefix());
        assertEquals(HASH, entity.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, entity.getStatus());
        assertEquals(CREATED_AT, entity.getCreatedAt());
        assertNull(entity.getRevokedAt());

        ApiCredential mappedBack = mapper.toDomain(entity);
        assertNotNull(mappedBack);
        assertEquals(id, mappedBack.getId());
        assertEquals(merchantId, mappedBack.getMerchantId());
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
        UUID merchantId = UUID.randomUUID();
        Instant revokedAt = Instant.parse("2026-09-28T12:00:00Z");

        ApiCredential domain = ApiCredential.reconstitute(
                id,
                merchantId,
                PREFIX,
                HASH,
                CredentialStatus.REVOKED,
                CREATED_AT,
                revokedAt
        );

        ApiCredentialEntity entity = mapper.toEntity(domain);
        assertNotNull(entity);
        assertEquals(CredentialStatus.REVOKED, entity.getStatus());
        assertEquals(revokedAt, entity.getRevokedAt());

        ApiCredential mappedBack = mapper.toDomain(entity);
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
