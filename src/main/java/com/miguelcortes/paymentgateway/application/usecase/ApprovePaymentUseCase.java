package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.Processor;

import java.util.UUID;

public class ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final ProcessorRepositoryPort processorRepositoryPort;

    public ApprovePaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.processorRepositoryPort = processorRepositoryPort;
    }

    public Payment execute(UUID paymentId, UUID processorId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment ID must not be null");
        }
        if (processorId == null) {
            throw new IllegalArgumentException("Processor ID must not be null");
        }

        Processor processor = processorRepositoryPort.findById(processorId)
                .orElseThrow(() -> new ProcessorNotFoundException(processorId));

        if (!processor.isActive()) {
            throw new ProcessorSuspendedException(processorId);
        }

        Payment payment = paymentRepositoryPort.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.approve();

        paymentRepositoryPort.save(payment);
        return payment;
    }
}
