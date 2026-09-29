package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateRefundIdempotencyKeyException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.RefundMapper;
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
        RefundPersistenceAdapter.class,
        RefundMapper.class,
        PaymentPersistenceAdapter.class,
        PaymentMapper.class,
        MerchantPersistenceAdapter.class,
        MerchantMapper.class
})
@Testcontainers
class RefundPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private RefundPersistenceAdapter refundAdapter;

    @Autowired
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    private Payment createAndPersistMerchantAndPayment(UUID merchantId, UUID paymentId) {
        Merchant merchant = new Merchant(merchantId, "Merchant Test", "m_" + merchantId + "@test.com", Instant.now());
        merchantAdapter.save(merchant);

        Payment payment = new Payment(
                paymentId,
                merchantId,
                100000L,
                Currency.COP,
                "pay-key-" + paymentId,
                Instant.now()
        );
        payment.approve();
        paymentAdapter.save(payment);
        return payment;
    }

    @Test
    @DisplayName("Should save and retrieve Refund by ID")
    void shouldSaveAndRetrieveRefundById() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        UUID refundId = UUID.randomUUID();
        String idempotencyKey = "ref-it-001";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Refund refund = new Refund(
                refundId,
                paymentId,
                merchantId,
                40000L,
                Currency.COP,
                idempotencyKey,
                createdAt
        );

        refundAdapter.save(refund);

        Optional<Refund> byId = refundAdapter.findById(refundId);
        assertTrue(byId.isPresent());
        Refund saved = byId.get();
        assertEquals(refundId, saved.getId());
        assertEquals(paymentId, saved.getPaymentId());
        assertEquals(merchantId, saved.getMerchantId());
        assertEquals(40000L, saved.getAmount());
        assertEquals(Currency.COP, saved.getCurrency());
        assertEquals(RefundStatus.PENDING, saved.getStatus());
        assertEquals(idempotencyKey, saved.getIdempotencyKey());
        assertEquals(createdAt, saved.getCreatedAt());
        assertEquals(0L, saved.getVersion());
    }

    @Test
    @DisplayName("Should find Refund by ID and Merchant ID when matching")
    void shouldFindRefundByIdAndMerchantId() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId,
                paymentId,
                merchantId,
                25000L,
                Currency.COP,
                "ref-it-002",
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        refundAdapter.save(refund);

        Optional<Refund> found = refundAdapter.findByIdAndMerchantId(refundId, merchantId);
        assertTrue(found.isPresent());
        assertEquals(refundId, found.get().getId());
        assertEquals(merchantId, found.get().getMerchantId());
    }

    @Test
    @DisplayName("Should return empty Optional when Refund exists but Merchant ID does not match")
    void shouldReturnEmptyWhenRefundExistsForDifferentMerchant() {
        UUID ownerMerchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(ownerMerchantId, paymentId);

        UUID otherMerchantId = UUID.randomUUID();
        Merchant otherMerchant = new Merchant(otherMerchantId, "Other Merchant", "other@test.com", Instant.now());
        merchantAdapter.save(otherMerchant);

        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId,
                paymentId,
                ownerMerchantId,
                30000L,
                Currency.COP,
                "ref-it-003",
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        refundAdapter.save(refund);

        Optional<Refund> found = refundAdapter.findByIdAndMerchantId(refundId, otherMerchantId);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should find Refund by Merchant ID and Idempotency Key")
    void shouldFindByMerchantIdAndIdempotencyKey() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        String idempotencyKey = "ref-it-idemp-1";
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId,
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                idempotencyKey,
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        refundAdapter.save(refund);

        Optional<Refund> found = refundAdapter.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        assertTrue(found.isPresent());
        assertEquals(refundId, found.get().getId());
        assertEquals(idempotencyKey, found.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("Should allow same idempotency key for different merchants")
    void shouldAllowSameIdempotencyKeyForDifferentMerchants() {
        UUID merchant1 = UUID.randomUUID();
        UUID payment1 = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchant1, payment1);

        UUID merchant2 = UUID.randomUUID();
        UUID payment2 = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchant2, payment2);

        String sharedKey = "shared-idempotency-key";

        Refund refund1 = new Refund(
                UUID.randomUUID(),
                payment1,
                merchant1,
                10000L,
                Currency.COP,
                sharedKey,
                Instant.now()
        );

        Refund refund2 = new Refund(
                UUID.randomUUID(),
                payment2,
                merchant2,
                20000L,
                Currency.COP,
                sharedKey,
                Instant.now()
        );

        refundAdapter.save(refund1);
        refundAdapter.save(refund2);

        Optional<Refund> found1 = refundAdapter.findByMerchantIdAndIdempotencyKey(merchant1, sharedKey);
        Optional<Refund> found2 = refundAdapter.findByMerchantIdAndIdempotencyKey(merchant2, sharedKey);

        assertTrue(found1.isPresent());
        assertTrue(found2.isPresent());
        assertEquals(refund1.getId(), found1.get().getId());
        assertEquals(refund2.getId(), found2.get().getId());
    }

    @Test
    @DisplayName("Should translate duplicate (merchantId, idempotencyKey) to DuplicateRefundIdempotencyKeyException")
    void shouldTranslateDuplicateMerchantIdempotencyKeyConstraint() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        String duplicateKey = "ref-duplicate-key";

        Refund refund1 = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                10000L,
                Currency.COP,
                duplicateKey,
                Instant.now()
        );
        refundAdapter.save(refund1);

        Refund refund2 = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                20000L,
                Currency.COP,
                duplicateKey,
                Instant.now()
        );

        DuplicateRefundIdempotencyKeyException ex = assertThrows(
                DuplicateRefundIdempotencyKeyException.class,
                () -> refundAdapter.save(refund2)
        );

        assertTrue(ex.getMessage().contains(duplicateKey));
    }

    @Test
    @DisplayName("Should propagate DataIntegrityViolationException on invalid payment FK and NOT DuplicateRefundIdempotencyKeyException")
    void shouldPropagateDataIntegrityViolationOnInvalidPaymentForeignKey() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Merchant Valid", "m_val@test.com", Instant.now());
        merchantAdapter.save(merchant);

        UUID nonExistentPaymentId = UUID.randomUUID();

        Refund orphanRefund = new Refund(
                UUID.randomUUID(),
                nonExistentPaymentId,
                merchantId,
                10000L,
                Currency.COP,
                "ref-orphan-payment",
                Instant.now()
        );

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> refundAdapter.save(orphanRefund)
        );

        assertNotNull(ex);
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        Throwable root = ex.getRootCause();
        String rootMsg = root != null && root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        assertTrue(msg.contains("fk_refunds_payments") || rootMsg.contains("fk_refunds_payments"));
    }

    @Test
    @DisplayName("Should propagate DataIntegrityViolationException on invalid merchant FK and NOT DuplicateRefundIdempotencyKeyException")
    void shouldPropagateDataIntegrityViolationOnInvalidMerchantForeignKey() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        UUID nonExistentMerchantId = UUID.randomUUID();

        Refund orphanRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                nonExistentMerchantId,
                10000L,
                Currency.COP,
                "ref-orphan-merchant",
                Instant.now()
        );

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> refundAdapter.save(orphanRefund)
        );

        assertNotNull(ex);
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        Throwable root = ex.getRootCause();
        String rootMsg = root != null && root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        assertTrue(msg.contains("fk_refunds_merchants") || rootMsg.contains("fk_refunds_merchants"));
    }
}
