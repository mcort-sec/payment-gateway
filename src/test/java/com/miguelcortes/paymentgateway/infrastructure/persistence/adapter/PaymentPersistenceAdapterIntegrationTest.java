package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
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
import java.util.List;
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

    @Test
    @DisplayName("Should paginate payments by merchant ID with strict tenant isolation")
    void shouldPaginatePaymentsByMerchantIdWithTenantIsolation() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();

        merchantAdapter.save(new Merchant(merchantA, "Merchant A", "a@test.com", Instant.now()));
        merchantAdapter.save(new Merchant(merchantB, "Merchant B", "b@test.com", Instant.now()));

        Payment payA1 = new Payment(UUID.randomUUID(), merchantA, 1000L, Currency.USD, "k-a1", Instant.now());
        Payment payA2 = new Payment(UUID.randomUUID(), merchantA, 2000L, Currency.USD, "k-a2", Instant.now().plusSeconds(1));
        Payment payB1 = new Payment(UUID.randomUUID(), merchantB, 3000L, Currency.USD, "k-b1", Instant.now());

        adapter.save(payA1);
        adapter.save(payA2);
        adapter.save(payB1);

        PageResult<Payment> resultA = adapter.findByMerchantId(merchantA, new PageQuery(0, 10));

        assertEquals(2, resultA.items().size());
        assertEquals(2L, resultA.totalElements());
        assertEquals(1, resultA.totalPages());
        assertEquals(0, resultA.page());
        assertEquals(10, resultA.size());
        assertTrue(resultA.items().stream().allMatch(p -> p.getMerchantId().equals(merchantA)));
    }

    @Test
    @DisplayName("Should sort payments by createdAt DESC and id DESC deterministically")
    void shouldSortPaymentsByCreatedAtDescAndIdDescDeterministically() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Sort", "sort@test.com", Instant.now()));

        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant tOldest = baseTime.minusSeconds(20);
        Instant tMiddle = baseTime.minusSeconds(10);
        Instant tNewest = baseTime;
        Instant tSame = baseTime.plusSeconds(10);

        UUID sameId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sameId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Payment pOldest = new Payment(UUID.randomUUID(), merchantId, 100L, Currency.USD, "k-old", tOldest);
        Payment pMiddle = new Payment(UUID.randomUUID(), merchantId, 200L, Currency.USD, "k-mid", tMiddle);
        Payment pNewest = new Payment(UUID.randomUUID(), merchantId, 300L, Currency.USD, "k-new", tNewest);
        Payment pSame1 = new Payment(sameId1, merchantId, 400L, Currency.USD, "k-same1", tSame);
        Payment pSame2 = new Payment(sameId2, merchantId, 500L, Currency.USD, "k-same2", tSame);

        adapter.save(pOldest);
        adapter.save(pMiddle);
        adapter.save(pNewest);
        adapter.save(pSame1);
        adapter.save(pSame2);

        PageResult<Payment> result = adapter.findByMerchantId(merchantId, new PageQuery(0, 10));

        List<Payment> items = result.items();
        assertEquals(5, items.size());

        // Top 2 have tSame, ordered by id DESC: sameId2 before sameId1
        assertEquals(pSame2.getId(), items.get(0).getId());
        assertEquals(pSame1.getId(), items.get(1).getId());
        assertEquals(pNewest.getId(), items.get(2).getId());
        assertEquals(pMiddle.getId(), items.get(3).getId());
        assertEquals(pOldest.getId(), items.get(4).getId());
    }

    @Test
    @DisplayName("Should paginate payments across multiple pages without duplicates")
    void shouldPaginatePaymentsAcrossMultiplePagesWithoutDuplicates() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Pages", "pages@test.com", Instant.now()));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 5; i++) {
            Payment p = new Payment(
                    UUID.randomUUID(),
                    merchantId,
                    (i + 1) * 1000L,
                    Currency.COP,
                    "k-page-" + i,
                    now.plusSeconds(i)
            );
            adapter.save(p);
        }

        PageResult<Payment> page0 = adapter.findByMerchantId(merchantId, new PageQuery(0, 2));
        assertEquals(2, page0.items().size());
        assertEquals(5L, page0.totalElements());
        assertEquals(3, page0.totalPages());
        assertEquals(0, page0.page());

        PageResult<Payment> page1 = adapter.findByMerchantId(merchantId, new PageQuery(1, 2));
        assertEquals(2, page1.items().size());
        assertEquals(5L, page1.totalElements());
        assertEquals(3, page1.totalPages());
        assertEquals(1, page1.page());

        PageResult<Payment> page2 = adapter.findByMerchantId(merchantId, new PageQuery(2, 2));
        assertEquals(1, page2.items().size());
        assertEquals(5L, page2.totalElements());
        assertEquals(3, page2.totalPages());
        assertEquals(2, page2.page());

        // Ensure distinct items across all pages
        List<UUID> page0Ids = page0.items().stream().map(Payment::getId).toList();
        List<UUID> page1Ids = page1.items().stream().map(Payment::getId).toList();
        List<UUID> page2Ids = page2.items().stream().map(Payment::getId).toList();

        assertTrue(page0Ids.stream().noneMatch(page1Ids::contains));
        assertTrue(page0Ids.stream().noneMatch(page2Ids::contains));
        assertTrue(page1Ids.stream().noneMatch(page2Ids::contains));
    }

    @Test
    @DisplayName("Should return empty PageResult when merchant has no payments")
    void shouldReturnEmptyPageResultWhenMerchantHasNoPayments() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Empty", "empty@test.com", Instant.now()));

        PageResult<Payment> result = adapter.findByMerchantId(merchantId, new PageQuery(0, 20));

        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.totalElements());
        assertEquals(0, result.totalPages());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
    }
}
