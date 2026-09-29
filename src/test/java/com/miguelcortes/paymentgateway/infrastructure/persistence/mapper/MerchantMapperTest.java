package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.MerchantEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MerchantMapperTest {

    private MerchantMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MerchantMapper();
    }

    @Test
    @DisplayName("Should map domain Merchant to MerchantEntity preserving all fields")
    void shouldMapDomainToEntity() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
        Merchant domain = new Merchant(id, "Shop 1", "shop1@store.com", MerchantStatus.ACTIVE, createdAt);

        MerchantEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals("Shop 1", entity.getName());
        assertEquals("shop1@store.com", entity.getEmail());
        assertEquals(MerchantStatus.ACTIVE, entity.getStatus());
        assertEquals(createdAt, entity.getCreatedAt());
    }

    @Test
    @DisplayName("Should map MerchantEntity to domain Merchant preserving all fields")
    void shouldMapEntityToDomain() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T13:00:00Z");
        MerchantEntity entity = new MerchantEntity(id, "Shop 2", "shop2@store.com", MerchantStatus.SUSPENDED, createdAt);

        Merchant domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(id, domain.getId());
        assertEquals("Shop 2", domain.getName());
        assertEquals("shop2@store.com", domain.getEmail());
        assertEquals(MerchantStatus.SUSPENDED, domain.getStatus());
        assertEquals(createdAt, domain.getCreatedAt());
    }

    @Test
    @DisplayName("Should return null when mapping null objects")
    void shouldReturnNullWhenMappingNull() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }
}
