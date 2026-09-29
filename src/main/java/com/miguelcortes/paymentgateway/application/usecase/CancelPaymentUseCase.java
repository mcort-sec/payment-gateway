package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.util.UUID;

public class CancelPaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;

    public CancelPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    public Payment execute(UUID id) {
        Payment payment = paymentRepositoryPort.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        payment.cancel();

        paymentRepositoryPort.save(payment);
        return payment;
    }
}
