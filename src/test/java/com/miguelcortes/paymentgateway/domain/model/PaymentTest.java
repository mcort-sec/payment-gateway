package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
