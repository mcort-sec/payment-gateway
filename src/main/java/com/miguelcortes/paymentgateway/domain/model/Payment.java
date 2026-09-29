package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private final UUID id;
    private final UUID customerId;
    private final long amount;
    private final Currency currency;
    private PaymentStatus status;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Long version;

    public Payment(
            UUID id,
            UUID customerId,
            long amount,
            Currency currency,
            String idempotencyKey,
            Instant createdAt
    ) {
        this(id, customerId, amount, currency, PaymentStatus.PENDING, idempotencyKey, createdAt, null);
    }

    private Payment(
            UUID id,
            UUID customerId,
            long amount,
            Currency currency,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt,
            Long version
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
        if (status == null) {
            throw new InvalidPaymentException("Payment status cannot be null");
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
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static Payment reconstitute(
            UUID id,
            UUID customerId,
            long amount,
            Currency currency,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt,
            Long version
    ) {
        if (version == null) {
            throw new InvalidPaymentException("Persisted payment must have a version");
        }
        return new Payment(id, customerId, amount, currency, status, idempotencyKey, createdAt, version);
    }

    public void approve() {
        ensurePending("approve");
        this.status = PaymentStatus.APPROVED;
    }

    public void decline() {
        ensurePending("decline");
        this.status = PaymentStatus.DECLINED;
    }

    public void cancel() {
        ensurePending("cancel");
        this.status = PaymentStatus.CANCELLED;
    }

    private void ensurePending(String action) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Cannot " + action + " payment with status " + this.status
            );
        }
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

    public Long getVersion() {
        return version;
    }
}
