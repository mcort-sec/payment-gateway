package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Refund {

    private final UUID id;
    private final UUID paymentId;
    private final UUID merchantId;
    private final long amount;
    private final Currency currency;
    private RefundStatus status;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Long version;

    public Refund(
            UUID id,
            UUID paymentId,
            UUID merchantId,
            long amount,
            Currency currency,
            String idempotencyKey,
            Instant createdAt
    ) {
        this(id, paymentId, merchantId, amount, currency, RefundStatus.PENDING, idempotencyKey, createdAt, null);
    }

    private Refund(
            UUID id,
            UUID paymentId,
            UUID merchantId,
            long amount,
            Currency currency,
            RefundStatus status,
            String idempotencyKey,
            Instant createdAt,
            Long version
    ) {
        if (id == null) {
            throw new InvalidRefundException("Refund ID cannot be null");
        }
        if (paymentId == null) {
            throw new InvalidRefundException("Payment ID cannot be null");
        }
        if (merchantId == null) {
            throw new InvalidRefundException("Merchant ID cannot be null");
        }
        if (amount <= 0) {
            throw new InvalidRefundException("Amount must be greater than 0");
        }
        if (currency == null) {
            throw new InvalidRefundException("Currency cannot be null");
        }
        if (status == null) {
            throw new InvalidRefundException("Refund status cannot be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRefundException("Idempotency key cannot be null, empty, or blank");
        }
        if (createdAt == null) {
            throw new InvalidRefundException("Creation timestamp cannot be null");
        }

        this.id = id;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static Refund reconstitute(
            UUID id,
            UUID paymentId,
            UUID merchantId,
            long amount,
            Currency currency,
            RefundStatus status,
            String idempotencyKey,
            Instant createdAt,
            Long version
    ) {
        if (version == null) {
            throw new InvalidRefundException("Persisted refund must have a version");
        }
        return new Refund(id, paymentId, merchantId, amount, currency, status, idempotencyKey, createdAt, version);
    }

    public void approve() {
        ensurePending("approve");
        this.status = RefundStatus.APPROVED;
    }

    public void decline() {
        ensurePending("decline");
        this.status = RefundStatus.DECLINED;
    }

    private void ensurePending(String action) {
        if (this.status != RefundStatus.PENDING) {
            throw new InvalidRefundStateException(
                    "Cannot " + action + " refund with status " + this.status
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Refund refund)) return false;
        return Objects.equals(id, refund.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
