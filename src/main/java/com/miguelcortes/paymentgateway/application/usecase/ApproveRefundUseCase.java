package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.exception.RefundNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.Refund;

import java.util.UUID;

public class ApproveRefundUseCase {

    private final RefundRepositoryPort refundRepositoryPort;
    private final ProcessorRepositoryPort processorRepositoryPort;

    public ApproveRefundUseCase(
            RefundRepositoryPort refundRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        this.refundRepositoryPort = refundRepositoryPort;
        this.processorRepositoryPort = processorRepositoryPort;
    }

    public Refund execute(UUID refundId, UUID processorId) {
        if (refundId == null) {
            throw new IllegalArgumentException("Refund ID must not be null");
        }
        if (processorId == null) {
            throw new IllegalArgumentException("Processor ID must not be null");
        }

        Processor processor = processorRepositoryPort.findById(processorId)
                .orElseThrow(() -> new ProcessorNotFoundException(processorId));

        if (!processor.isActive()) {
            throw new ProcessorSuspendedException(processorId);
        }

        Refund refund = refundRepositoryPort.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException(refundId));

        refund.approve();

        refundRepositoryPort.save(refund);
        return refund;
    }
}
