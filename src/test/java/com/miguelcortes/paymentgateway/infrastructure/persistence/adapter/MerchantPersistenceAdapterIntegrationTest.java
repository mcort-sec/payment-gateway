package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateMerchantEmailException;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MerchantPersistenceAdapter.class, MerchantMapper.class})
@Testcontainers
class MerchantPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MerchantPersistenceAdapter adapter;

    @Test
    @DisplayName("Should save and retrieve merchant by ID and by email in PostgreSQL")
    void shouldSaveAndRetrieveMerchantByIdAndEmail() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Merchant merchant = new Merchant(
                id,
                "Mega Store",
                "mega@store.com",
                createdAt
        );

        adapter.save(merchant);

        Optional<Merchant> byId = adapter.findById(id);
        assertTrue(byId.isPresent());
        Merchant foundById = byId.get();
        assertEquals(id, foundById.getId());
        assertEquals("Mega Store", foundById.getName());
        assertEquals("mega@store.com", foundById.getEmail());
        assertEquals(MerchantStatus.ACTIVE, foundById.getStatus());
        assertEquals(createdAt, foundById.getCreatedAt());

        Optional<Merchant> byEmail = adapter.findByEmail("mega@store.com");
        assertTrue(byEmail.isPresent());
        assertEquals(id, byEmail.get().getId());
    }

    @Test
    @DisplayName("Should translate uq_merchants_email constraint violation to DuplicateMerchantEmailException")
    void shouldTranslatePostgresUniqueEmailConstraintToDuplicateMerchantEmailException() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Merchant merchant1 = new Merchant(id1, "Store One", "duplicate@store.com", Instant.now());
        Merchant merchant2 = new Merchant(id2, "Store Two", "duplicate@store.com", Instant.now());

        adapter.save(merchant1);

        DuplicateMerchantEmailException exception = assertThrows(
                DuplicateMerchantEmailException.class,
                () -> adapter.save(merchant2)
        );

        assertTrue(exception.getMessage().contains("duplicate@store.com"));
    }
}
