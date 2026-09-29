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

    public Payment execute(UUID id) {
        return paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
