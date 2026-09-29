package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class PaymentPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private PaymentPersistenceAdapter adapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Test
    void shouldSaveAndRetrieveNewPaymentByIdAndByMerchantAndIdempotencyKey() {
        UUID id = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant IT 1", "m_it1@test.com", Instant.now()));

        String idempotencyKey = "req-it-001";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Payment newPayment = new Payment(
                id,
                merchantId,
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
        assertEquals(merchantId, savedById.getMerchantId());
        assertEquals(150000L, savedById.getAmount());
        assertEquals(Currency.COP, savedById.getCurrency());
        assertEquals(PaymentStatus.PENDING, savedById.getStatus());
        assertEquals(idempotencyKey, savedById.getIdempotencyKey());
        assertEquals(createdAt, savedById.getCreatedAt());
        assertEquals(0L, savedById.getVersion());

        Optional<Payment> byMerchantAndKey = adapter.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        assertTrue(byMerchantAndKey.isPresent());
        Payment savedByKey = byMerchantAndKey.get();
        assertEquals(id, savedByKey.getId());
        assertEquals(PaymentStatus.PENDING, savedByKey.getStatus());
        assertEquals(0L, savedByKey.getVersion());
    }

    @Test
    void shouldIncrementVersionWhenUpdatingExistingPaymentState() {
        UUID id = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant IT 2", "m_it2@test.com", Instant.now()));

        String idempotencyKey = "req-it-version-inc";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Payment newPayment = new Payment(
                id,
                merchantId,
                50000L,
                Currency.USD,
                idempotencyKey,
                createdAt
        );

        adapter.save(newPayment);

        Payment retrievedV0 = adapter.findById(id).orElseThrow();
        assertEquals(PaymentStatus.PENDING, retrievedV0.getStatus());
        assertEquals(0L, retrievedV0.getVersion());

        retrievedV0.approve();
        adapter.save(retrievedV0);

        Payment retrievedV1 = adapter.findById(id).orElseThrow();
        assertEquals(PaymentStatus.APPROVED, retrievedV1.getStatus());
        assertEquals(1L, retrievedV1.getVersion());
    }

    @Test
    void shouldTranslatePostgresUniqueConstraintToDuplicateIdempotencyKeyException() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant IT 3", "m_it3@test.com", Instant.now()));

        String idempotencyKey = "req-it-duplicate";

        Payment payment1 = new Payment(
                UUID.randomUUID(),
                merchantId,
                100000L,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );

        Payment payment2WithSameKey = new Payment(
                UUID.randomUUID(),
                merchantId,
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

    @Test
    void shouldFindPaymentByIdAndMerchantIdWhenMatches() {
        UUID id = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant IT 4", "m_it4@test.com", Instant.now()));

        Payment payment = new Payment(
                id,
                merchantId,
                75000L,
                Currency.COP,
                "req-it-find-scoped-1",
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        adapter.save(payment);

        Optional<Payment> found = adapter.findByIdAndMerchantId(id, merchantId);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
        assertEquals(merchantId, found.get().getMerchantId());
    }

    @Test
    void shouldReturnEmptyWhenPaymentExistsButMerchantIdDoesNotMatch() {
        UUID id = UUID.randomUUID();
        UUID ownerMerchantId = UUID.randomUUID();
        UUID otherMerchantId = UUID.randomUUID();

        merchantAdapter.save(new Merchant(ownerMerchantId, "Merchant Owner", "owner@test.com", Instant.now()));
        merchantAdapter.save(new Merchant(otherMerchantId, "Merchant Other", "other@test.com", Instant.now()));

        Payment payment = new Payment(
                id,
                ownerMerchantId,
                80000L,
                Currency.COP,
                "req-it-find-scoped-2",
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        adapter.save(payment);

        Optional<Payment> found = adapter.findByIdAndMerchantId(id, otherMerchantId);
        assertTrue(found.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPaymentIdDoesNotExist() {
        UUID nonExistentPaymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant IT 5", "m_it5@test.com", Instant.now()));

        Optional<Payment> found = adapter.findByIdAndMerchantId(nonExistentPaymentId, merchantId);
        assertTrue(found.isEmpty());
    }
}
