package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.util.UUID;

public class GetPaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;

    public GetPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    public Payment execute(UUID id, UUID requesterMerchantId) {
        if (id == null) {
            throw new IllegalArgumentException("Payment ID must not be null");
        }
        if (requesterMerchantId == null) {
            throw new IllegalArgumentException("Requester merchant ID must not be null");
        }
        return paymentRepositoryPort.findByIdAndMerchantId(id, requesterMerchantId)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
