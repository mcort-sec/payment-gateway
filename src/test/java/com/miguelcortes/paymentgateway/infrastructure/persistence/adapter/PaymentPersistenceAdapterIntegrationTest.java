package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
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
@Import({PaymentPersistenceAdapter.class, PaymentMapper.class})
@Testcontainers
class PaymentPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private PaymentPersistenceAdapter adapter;

    @Test
    void shouldSaveAndRetrieveNewPaymentByIdAndByCustomerAndIdempotencyKey() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "req-it-001";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Payment newPayment = new Payment(
                id,
                customerId,
                150000L,
                Currency.COP,
                idempotencyKey,
                createdAt
        );

        adapter.save(newPayment);

        Optional<Payment> byId = adapter.findById(id);
        assertTrue(byId.isPresent());
        Payment savedById = byId.get();
        assertEquals(id, savedById.getId());
        assertEquals(customerId, savedById.getCustomerId());
        assertEquals(150000L, savedById.getAmount());
        assertEquals(Currency.COP, savedById.getCurrency());
        assertEquals(PaymentStatus.PENDING, savedById.getStatus());
        assertEquals(idempotencyKey, savedById.getIdempotencyKey());
        assertEquals(createdAt, savedById.getCreatedAt());

        Optional<Payment> byCustomerAndKey = adapter.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        assertTrue(byCustomerAndKey.isPresent());
        Payment savedByKey = byCustomerAndKey.get();
        assertEquals(id, savedByKey.getId());
        assertEquals(PaymentStatus.PENDING, savedByKey.getStatus());
    }

    @Test
    void shouldSaveAndRetrievePaymentWithApprovedStatus() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "req-it-002";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Payment approvedPayment = Payment.reconstitute(
                id,
                customerId,
                50000L,
                Currency.USD,
                PaymentStatus.APPROVED,
                idempotencyKey,
                createdAt
        );

        adapter.save(approvedPayment);

        Optional<Payment> retrieved = adapter.findById(id);
        assertTrue(retrieved.isPresent());
        assertEquals(PaymentStatus.APPROVED, retrieved.get().getStatus());
        assertEquals(50000L, retrieved.get().getAmount());
        assertEquals(Currency.USD, retrieved.get().getCurrency());
    }

    @Test
    void shouldTranslatePostgresUniqueConstraintToDuplicateIdempotencyKeyException() {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "req-it-duplicate";

        Payment payment1 = new Payment(
                UUID.randomUUID(),
                customerId,
                100000L,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );

        Payment payment2WithSameKey = new Payment(
                UUID.randomUUID(),
                customerId,
                200000L,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );

        adapter.save(payment1);

        DuplicateIdempotencyKeyException exception = assertThrows(
                DuplicateIdempotencyKeyException.class,
                () -> adapter.save(payment2WithSameKey)
        );

        assertTrue(exception.getMessage().contains(idempotencyKey));
    }
}
