package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PaymentPersistenceAdapter.class,
        PaymentMapper.class,
        MerchantPersistenceAdapter.class,
        MerchantMapper.class
})
@Testcontainers
class MerchantPaymentForeignKeyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Test
    @DisplayName("Should successfully persist Payment when referenced Merchant exists in PostgreSQL")
    void shouldPersistPaymentWhenMerchantExists() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Valid Merchant", "valid@merchant.com", Instant.now());
        merchantAdapter.save(merchant);

        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                merchantId,
                100000L,
                Currency.COP,
                "req-fk-valid-1",
                Instant.now()
        );

        assertDoesNotThrow(() -> paymentAdapter.save(payment));

        Optional<Payment> found = paymentAdapter.findById(paymentId);
        assertTrue(found.isPresent());
        assertEquals(merchantId, found.get().getMerchantId());
    }

    @Test
    @DisplayName("Should throw DataIntegrityViolationException with fk_payments_merchants when merchant does not exist in PostgreSQL")
    void shouldFailWhenMerchantDoesNotExistDueToForeignKeyConstraint() {
        UUID nonExistentMerchantId = UUID.randomUUID();
        Payment payment = new Payment(
                UUID.randomUUID(),
                nonExistentMerchantId,
                100000L,
                Currency.COP,
                "req-fk-invalid-1",
                Instant.now()
        );

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentAdapter.save(payment)
        );

        // Verify the underlying message references the foreign key constraint
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        Throwable rootCause = exception.getRootCause();
        String rootMessage = rootCause != null && rootCause.getMessage() != null ? rootCause.getMessage().toLowerCase() : "";

        assertTrue(
                message.contains("fk_payments_merchants") || rootMessage.contains("fk_payments_merchants"),
                "Exception should reference constraint 'fk_payments_merchants'"
        );
    }
}
