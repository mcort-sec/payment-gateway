package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ProcessorMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProcessorPersistenceAdapter.class, ProcessorMapper.class})
@Testcontainers
class ProcessorPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ProcessorPersistenceAdapter adapter;

    @Test
    @DisplayName("Should save and retrieve processor by ID in PostgreSQL")
    void shouldSaveAndRetrieveProcessorById() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Processor processor = new Processor(
                id,
                "Mastercard Net",
                createdAt
        );

        adapter.save(processor);

        Optional<Processor> byId = adapter.findById(id);
        assertTrue(byId.isPresent());
        Processor foundById = byId.get();
        assertEquals(id, foundById.getId());
        assertEquals("Mastercard Net", foundById.getName());
        assertEquals(ProcessorStatus.ACTIVE, foundById.getStatus());
        assertEquals(createdAt, foundById.getCreatedAt());
    }
}
