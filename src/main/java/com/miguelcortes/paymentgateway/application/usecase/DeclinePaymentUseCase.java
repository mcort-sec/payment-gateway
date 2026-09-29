package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.util.UUID;

public class DeclinePaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;

    public DeclinePaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    public Payment execute(UUID id) {
        Payment payment = paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        payment.decline();

        paymentRepositoryPort.save(payment);
        return payment;
    }
}
