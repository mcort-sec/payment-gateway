package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundTest {

    @Test
    @DisplayName("Should create valid refund with PENDING status, null version, and retain values")
    void shouldCreateValidRefundWithPendingStatusAndNullVersion() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        long amount = 50000L;
        Currency currency = Currency.COP;
        String idempotencyKey = "ref-key-1";
        Instant createdAt = Instant.now();

        Refund refund = new Refund(id, paymentId, merchantId, amount, currency, idempotencyKey, createdAt);

        assertEquals(id, refund.getId());
        assertEquals(paymentId, refund.getPaymentId());
        assertEquals(merchantId, refund.getMerchantId());
        assertEquals(amount, refund.getAmount());
        assertEquals(currency, refund.getCurrency());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(idempotencyKey, refund.getIdempotencyKey());
        assertEquals(createdAt, refund.getCreatedAt());
        assertNull(refund.getVersion());
    }

    @Test
    @DisplayName("Should transition from PENDING to APPROVED")
    void shouldTransitionFromPendingToApproved() {
        Refund refund = createValidRefund();

        refund.approve();

        assertEquals(RefundStatus.APPROVED, refund.getStatus());
    }

    @Test
    @DisplayName("Should transition from PENDING to DECLINED")
    void shouldTransitionFromPendingToDeclined() {
        Refund refund = createValidRefund();

        refund.decline();

        assertEquals(RefundStatus.DECLINED, refund.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when approving already APPROVED refund")
    void shouldThrowExceptionWhenApprovingApprovedRefund() {
        Refund refund = createValidRefund();
        refund.approve();

        InvalidRefundStateException exception = assertThrows(
                InvalidRefundStateException.class,
                refund::approve
        );

        assertEquals("Cannot approve refund with status APPROVED", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when approving DECLINED refund")
    void shouldThrowExceptionWhenApprovingDeclinedRefund() {
        Refund refund = createValidRefund();
        refund.decline();

        InvalidRefundStateException exception = assertThrows(
                InvalidRefundStateException.class,
                refund::approve
        );

        assertEquals("Cannot approve refund with status DECLINED", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when declining already APPROVED refund")
    void shouldThrowExceptionWhenDecliningApprovedRefund() {
        Refund refund = createValidRefund();
        refund.approve();

        InvalidRefundStateException exception = assertThrows(
                InvalidRefundStateException.class,
                refund::decline
        );

        assertEquals("Cannot decline refund with status APPROVED", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when declining already DECLINED refund")
    void shouldThrowExceptionWhenDecliningDeclinedRefund() {
        Refund refund = createValidRefund();
        refund.decline();

        InvalidRefundStateException exception = assertThrows(
                InvalidRefundStateException.class,
                refund::decline
        );

        assertEquals("Cannot decline refund with status DECLINED", exception.getMessage());
    }

    @Test
    @DisplayName("Should reconstitute persisted refund with specified status and version")
    void shouldReconstituteRefundWithSpecifiedStatusAndVersion() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");

        Refund refund = Refund.reconstitute(
                id,
                paymentId,
                merchantId,
                75000L,
                Currency.COP,
                RefundStatus.APPROVED,
                "ref-reconstitute-1",
                createdAt,
                1L
        );

        assertEquals(id, refund.getId());
        assertEquals(paymentId, refund.getPaymentId());
        assertEquals(merchantId, refund.getMerchantId());
        assertEquals(75000L, refund.getAmount());
        assertEquals(Currency.COP, refund.getCurrency());
        assertEquals(RefundStatus.APPROVED, refund.getStatus());
        assertEquals("ref-reconstitute-1", refund.getIdempotencyKey());
        assertEquals(createdAt, refund.getCreatedAt());
        assertEquals(1L, refund.getVersion());
    }

    @Test
    @DisplayName("Should throw exception when reconstituting with null version")
    void shouldThrowExceptionWhenReconstitutingWithNullVersion() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> Refund.reconstitute(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        RefundStatus.PENDING,
                        "key-1",
                        Instant.now(),
                        null
                )
        );

        assertEquals("Persisted refund must have a version", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when ID is null")
    void shouldThrowExceptionWhenIdIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        null,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Refund ID cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when paymentId is null")
    void shouldThrowExceptionWhenPaymentIdIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Payment ID cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when merchantId is null")
    void shouldThrowExceptionWhenMerchantIdIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        1000L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Merchant ID cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when amount is zero")
    void shouldThrowExceptionWhenAmountIsZero() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        0L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Amount must be greater than 0", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when amount is negative")
    void shouldThrowExceptionWhenAmountIsNegative() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        -100L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Amount must be greater than 0", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when currency is null")
    void shouldThrowExceptionWhenCurrencyIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        null,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Currency cannot be null", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Should throw exception when idempotencyKey is empty or blank")
    void shouldThrowExceptionWhenIdempotencyKeyIsEmptyOrBlank(String blankKey) {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        blankKey,
                        Instant.now()
                )
        );

        assertEquals("Idempotency key cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when idempotencyKey is null")
    void shouldThrowExceptionWhenIdempotencyKeyIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        null,
                        Instant.now()
                )
        );

        assertEquals("Idempotency key cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when createdAt is null")
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> new Refund(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        "key-1",
                        null
                )
        );

        assertEquals("Creation timestamp cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when status is null in reconstitution")
    void shouldThrowExceptionWhenStatusIsNullInReconstitution() {
        InvalidRefundException exception = assertThrows(
                InvalidRefundException.class,
                () -> Refund.reconstitute(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        null,
                        "key-1",
                        Instant.now(),
                        0L
                )
        );

        assertEquals("Refund status cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should evaluate equality and hashCode based on ID")
    void shouldEvaluateEqualityAndHashCodeById() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Refund r1 = new Refund(id1, UUID.randomUUID(), UUID.randomUUID(), 1000L, Currency.USD, "k1", Instant.now());
        Refund r2 = new Refund(id1, UUID.randomUUID(), UUID.randomUUID(), 2000L, Currency.COP, "k2", Instant.now());
        Refund r3 = new Refund(id2, UUID.randomUUID(), UUID.randomUUID(), 1000L, Currency.USD, "k1", Instant.now());

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
        assertNotEquals(r1, null);
        assertNotEquals(r1, new Object());
    }

    private Refund createValidRefund() {
        return new Refund(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                50000L,
                Currency.COP,
                "ref-test-1",
                Instant.now()
        );
    }
}
