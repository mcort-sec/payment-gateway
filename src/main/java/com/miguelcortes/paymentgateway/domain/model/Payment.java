package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private UUID id;
    private UUID customerId;
    private long amount;
    private Currency currency;
    private PaymentStatus status;
    private String idempotencyKey;
    private Instant createdAt;

    public Payment(
            UUID id,
            UUID customerId,
            long amount,
            Currency currency,
            String idempotencyKey,
            Instant createdAt
    ) {
        if (id == null) {
            throw new InvalidPaymentException("Payment ID cannot be null");
        }
        if (customerId == null) {
            throw new InvalidPaymentException("Customer ID cannot be null");
        }
        if (amount <= 0) {
            throw new InvalidPaymentException("Amount must be greater than 0");
        }
        if (currency == null) {
            throw new InvalidPaymentException("Currency cannot be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidPaymentException("Idempotency key cannot be null, empty, or blank");
        }
        if (createdAt == null) {
            throw new InvalidPaymentException("Creation timestamp cannot be null");
        }

        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public long getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
