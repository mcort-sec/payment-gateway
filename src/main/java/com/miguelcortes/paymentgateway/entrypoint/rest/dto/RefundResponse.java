package com.miguelcortes.paymentgateway.entrypoint.rest.dto;

import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID merchantId,
        long amount,
        Currency currency,
        RefundStatus status,
        Instant createdAt
) {
    public static RefundResponse fromDomain(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getMerchantId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getCreatedAt()
        );
    }
}
