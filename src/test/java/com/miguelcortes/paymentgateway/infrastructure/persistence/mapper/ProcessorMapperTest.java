package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessorMapperTest {

    private ProcessorMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProcessorMapper();
    }

    @Test
    @DisplayName("Should map domain Processor to ProcessorEntity preserving all fields")
    void shouldMapDomainToEntity() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T12:00:00Z");
        Processor domain = new Processor(id, "Visa Net", ProcessorStatus.ACTIVE, createdAt);

        ProcessorEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals("Visa Net", entity.getName());
        assertEquals(ProcessorStatus.ACTIVE, entity.getStatus());
        assertEquals(createdAt, entity.getCreatedAt());
    }

    @Test
    @DisplayName("Should map ProcessorEntity to domain Processor preserving all fields")
    void shouldMapEntityToDomain() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T13:00:00Z");
        ProcessorEntity entity = new ProcessorEntity(id, "Mastercard Net", ProcessorStatus.SUSPENDED, createdAt);

        Processor domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(id, domain.getId());
        assertEquals("Mastercard Net", domain.getName());
        assertEquals(ProcessorStatus.SUSPENDED, domain.getStatus());
        assertEquals(createdAt, domain.getCreatedAt());
    }

    @Test
    @DisplayName("Should return null when mapping null objects")
    void shouldReturnNullWhenMappingNull() {
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toDomain(null));
    }
}
