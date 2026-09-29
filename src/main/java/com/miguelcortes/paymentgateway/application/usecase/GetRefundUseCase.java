package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.RefundNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Refund;

import java.util.UUID;

public class GetRefundUseCase {

    private final RefundRepositoryPort refundRepositoryPort;

    public GetRefundUseCase(RefundRepositoryPort refundRepositoryPort) {
        this.refundRepositoryPort = refundRepositoryPort;
    }

    public Refund execute(UUID refundId, UUID requesterMerchantId) {
        if (refundId == null) {
            throw new IllegalArgumentException("Refund ID must not be null");
        }
        if (requesterMerchantId == null) {
            throw new IllegalArgumentException("Requester merchant ID must not be null");
        }
        return refundRepositoryPort.findByIdAndMerchantId(refundId, requesterMerchantId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));
    }
}
