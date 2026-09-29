package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @Test
    void shouldCreateValidPaymentWithPendingStatusAndRetainValues() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        long amount = 150000L;
        Currency currency = Currency.COP;
        String idempotencyKey = "req-12345";
        Instant createdAt = Instant.now();

        Payment payment = new Payment(id, customerId, amount, currency, idempotencyKey, createdAt);

        assertEquals(id, payment.getId());
        assertEquals(customerId, payment.getCustomerId());
        assertEquals(amount, payment.getAmount());
        assertEquals(currency, payment.getCurrency());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(idempotencyKey, payment.getIdempotencyKey());
        assertEquals(createdAt, payment.getCreatedAt());
        assertNull(payment.getVersion());
    }

    @Test
    void shouldThrowExceptionWhenIdIsNull() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
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
    void shouldThrowExceptionWhenCustomerIdIsNull() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
                        UUID.randomUUID(),
                        null,
                        1000L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Customer ID cannot be null", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenAmountIsZero() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
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
    void shouldThrowExceptionWhenAmountIsNegative() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        -500L,
                        Currency.USD,
                        "key-1",
                        Instant.now()
                )
        );

        assertEquals("Amount must be greater than 0", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCurrencyIsNull() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
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

    @Test
    void shouldThrowExceptionWhenIdempotencyKeyIsNull() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
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
    void shouldThrowExceptionWhenIdempotencyKeyIsEmpty() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        "",
                        Instant.now()
                )
        );

        assertEquals("Idempotency key cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenIdempotencyKeyIsBlank() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        "   ",
                        Instant.now()
                )
        );

        assertEquals("Idempotency key cannot be null, empty, or blank", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> new Payment(
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
    void shouldTransitionFromPendingToApproved() {
        Payment payment = createValidPayment();

        payment.approve();

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    @Test
    void shouldTransitionFromPendingToDeclined() {
        Payment payment = createValidPayment();

        payment.decline();

        assertEquals(PaymentStatus.DECLINED, payment.getStatus());
    }

    @Test
    void shouldTransitionFromPendingToCancelled() {
        Payment payment = createValidPayment();

        payment.cancel();

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenCancellingApprovedPayment() {
        Payment payment = createValidPayment();
        payment.approve();

        InvalidPaymentStateException exception = assertThrows(
                InvalidPaymentStateException.class,
                payment::cancel
        );

        assertEquals("Cannot cancel payment with status APPROVED", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenApprovingDeclinedPayment() {
        Payment payment = createValidPayment();
        payment.decline();

        InvalidPaymentStateException exception = assertThrows(
                InvalidPaymentStateException.class,
                payment::approve
        );

        assertEquals("Cannot approve payment with status DECLINED", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenApprovingCancelledPayment() {
        Payment payment = createValidPayment();
        payment.cancel();

        InvalidPaymentStateException exception = assertThrows(
                InvalidPaymentStateException.class,
                payment::approve
        );

        assertEquals("Cannot approve payment with status CANCELLED", exception.getMessage());
    }

    @Test
    void shouldReconstitutePaymentWithApprovedStatusAndVersion() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T08:00:00Z");

        Payment payment = Payment.reconstitute(
                id,
                customerId,
                250000L,
                Currency.COP,
                PaymentStatus.APPROVED,
                "req-reconstitute-1",
                createdAt,
                0L
        );

        assertEquals(id, payment.getId());
        assertEquals(customerId, payment.getCustomerId());
        assertEquals(250000L, payment.getAmount());
        assertEquals(Currency.COP, payment.getCurrency());
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals("req-reconstitute-1", payment.getIdempotencyKey());
        assertEquals(createdAt, payment.getCreatedAt());
        assertEquals(0L, payment.getVersion());
    }

    @Test
    void shouldReconstitutePaymentWithDeclinedStatus() {
        Payment payment = Payment.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                50000L,
                Currency.USD,
                PaymentStatus.DECLINED,
                "req-reconstitute-2",
                Instant.now(),
                1L
        );

        assertEquals(PaymentStatus.DECLINED, payment.getStatus());
        assertEquals(1L, payment.getVersion());
    }

    @Test
    void shouldReconstitutePaymentWithCancelledStatus() {
        Payment payment = Payment.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                75000L,
                Currency.COP,
                PaymentStatus.CANCELLED,
                "req-reconstitute-3",
                Instant.now(),
                2L
        );

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(2L, payment.getVersion());
    }

    @Test
    void shouldThrowExceptionWhenReconstitutingWithNullStatus() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> Payment.reconstitute(
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

        assertEquals("Payment status cannot be null", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenReconstitutingWithNullVersion() {
        InvalidPaymentException exception = assertThrows(
                InvalidPaymentException.class,
                () -> Payment.reconstitute(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1000L,
                        Currency.USD,
                        PaymentStatus.PENDING,
                        "key-1",
                        Instant.now(),
                        null
                )
        );

        assertEquals("Persisted payment must have a version", exception.getMessage());
    }

    private Payment createValidPayment() {
        return new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                150000L,
                Currency.COP,
                "req-12345",
                Instant.now()
        );
    }
}
