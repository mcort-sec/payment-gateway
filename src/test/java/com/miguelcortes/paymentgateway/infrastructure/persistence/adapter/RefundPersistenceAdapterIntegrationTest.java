package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateRefundIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
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

import java.util.List;
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

    @Test
    @DisplayName("Should paginate refunds by merchant ID with strict tenant isolation")
    void shouldPaginateRefundsByMerchantIdWithTenantIsolation() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        UUID payA = UUID.randomUUID();
        UUID payB = UUID.randomUUID();

        createAndPersistMerchantAndPayment(merchantA, payA);
        createAndPersistMerchantAndPayment(merchantB, payB);

        Refund refA1 = new Refund(UUID.randomUUID(), payA, merchantA, 1000L, Currency.COP, "ref-a1", Instant.now());
        Refund refA2 = new Refund(UUID.randomUUID(), payA, merchantA, 2000L, Currency.COP, "ref-a2", Instant.now().plusSeconds(1));
        Refund refB1 = new Refund(UUID.randomUUID(), payB, merchantB, 3000L, Currency.COP, "ref-b1", Instant.now());

        refundAdapter.save(refA1);
        refundAdapter.save(refA2);
        refundAdapter.save(refB1);

        PageResult<Refund> resultA = refundAdapter.findByMerchantId(merchantA, new PageQuery(0, 10));

        assertEquals(2, resultA.items().size());
        assertEquals(2L, resultA.totalElements());
        assertEquals(1, resultA.totalPages());
        assertEquals(0, resultA.page());
        assertEquals(10, resultA.size());
        assertTrue(resultA.items().stream().allMatch(r -> r.getMerchantId().equals(merchantA)));
    }

    @Test
    @DisplayName("Should sort refunds by createdAt DESC and id DESC deterministically")
    void shouldSortRefundsByCreatedAtDescAndIdDescDeterministically() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant tOldest = baseTime.minusSeconds(20);
        Instant tMiddle = baseTime.minusSeconds(10);
        Instant tNewest = baseTime;
        Instant tSame = baseTime.plusSeconds(10);

        UUID sameId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sameId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Refund rOldest = new Refund(UUID.randomUUID(), paymentId, merchantId, 100L, Currency.COP, "k-rold", tOldest);
        Refund rMiddle = new Refund(UUID.randomUUID(), paymentId, merchantId, 200L, Currency.COP, "k-rmid", tMiddle);
        Refund rNewest = new Refund(UUID.randomUUID(), paymentId, merchantId, 300L, Currency.COP, "k-rnew", tNewest);
        Refund rSame1 = new Refund(sameId1, paymentId, merchantId, 400L, Currency.COP, "k-rsame1", tSame);
        Refund rSame2 = new Refund(sameId2, paymentId, merchantId, 500L, Currency.COP, "k-rsame2", tSame);

        refundAdapter.save(rOldest);
        refundAdapter.save(rMiddle);
        refundAdapter.save(rNewest);
        refundAdapter.save(rSame1);
        refundAdapter.save(rSame2);

        PageResult<Refund> result = refundAdapter.findByMerchantId(merchantId, new PageQuery(0, 10));

        List<Refund> items = result.items();
        assertEquals(5, items.size());

        assertEquals(rSame2.getId(), items.get(0).getId());
        assertEquals(rSame1.getId(), items.get(1).getId());
        assertEquals(rNewest.getId(), items.get(2).getId());
        assertEquals(rMiddle.getId(), items.get(3).getId());
        assertEquals(rOldest.getId(), items.get(4).getId());
    }

    @Test
    @DisplayName("Should paginate refunds across multiple pages without duplicates")
    void shouldPaginateRefundsAcrossMultiplePagesWithoutDuplicates() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createAndPersistMerchantAndPayment(merchantId, paymentId);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 5; i++) {
            Refund r = new Refund(
                    UUID.randomUUID(),
                    paymentId,
                    merchantId,
                    (i + 1) * 1000L,
                    Currency.COP,
                    "k-rpage-" + i,
                    now.plusSeconds(i)
            );
            refundAdapter.save(r);
        }

        PageResult<Refund> page0 = refundAdapter.findByMerchantId(merchantId, new PageQuery(0, 2));
        assertEquals(2, page0.items().size());
        assertEquals(5L, page0.totalElements());
        assertEquals(3, page0.totalPages());
        assertEquals(0, page0.page());

        PageResult<Refund> page1 = refundAdapter.findByMerchantId(merchantId, new PageQuery(1, 2));
        assertEquals(2, page1.items().size());
        assertEquals(5L, page1.totalElements());
        assertEquals(3, page1.totalPages());
        assertEquals(1, page1.page());

        PageResult<Refund> page2 = refundAdapter.findByMerchantId(merchantId, new PageQuery(2, 2));
        assertEquals(1, page2.items().size());
        assertEquals(5L, page2.totalElements());
        assertEquals(3, page2.totalPages());
        assertEquals(2, page2.page());

        List<UUID> page0Ids = page0.items().stream().map(Refund::getId).toList();
        List<UUID> page1Ids = page1.items().stream().map(Refund::getId).toList();
        List<UUID> page2Ids = page2.items().stream().map(Refund::getId).toList();

        assertTrue(page0Ids.stream().noneMatch(page1Ids::contains));
        assertTrue(page0Ids.stream().noneMatch(page2Ids::contains));
        assertTrue(page1Ids.stream().noneMatch(page2Ids::contains));
    }

    @Test
    @DisplayName("Should return empty PageResult when merchant has no refunds")
    void shouldReturnEmptyPageResultWhenMerchantHasNoRefunds() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Empty Refund", "emptyref@test.com", Instant.now()));

        PageResult<Refund> result = refundAdapter.findByMerchantId(merchantId, new PageQuery(0, 20));

        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.totalElements());
        assertEquals(0, result.totalPages());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
    }
}
