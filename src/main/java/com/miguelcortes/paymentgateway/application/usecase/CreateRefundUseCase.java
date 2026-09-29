package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateRefundCommand;
import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.application.exception.DuplicateRefundIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.RefundAmountExceedsAvailableException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundReservationPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.domain.model.Refund;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class CreateRefundUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final RefundRepositoryPort refundRepositoryPort;
    private final RefundReservationPort refundReservationPort;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreateRefundUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            RefundRepositoryPort refundRepositoryPort,
            RefundReservationPort refundReservationPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.refundRepositoryPort = refundRepositoryPort;
        this.refundReservationPort = refundReservationPort;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Refund execute(CreateRefundCommand command) {
        Payment payment = paymentRepositoryPort.findByIdAndMerchantId(command.paymentId(), command.merchantId())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));

        Optional<Refund> existingRefund = refundRepositoryPort
                .findByMerchantIdAndIdempotencyKey(command.merchantId(), command.idempotencyKey());

        if (existingRefund.isPresent()) {
            Refund refund = existingRefund.get();
            validateIdempotencyPayload(refund, command);
            return refund;
        }

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStateException(
                    "Cannot create refund for payment with status " + payment.getStatus()
            );
        }

        UUID id = idGenerator.generate();
        Instant createdAt = timeProvider.now();

        Refund newRefund = new Refund(
                id,
                command.paymentId(),
                command.merchantId(),
                command.amount(),
                payment.getCurrency(),
                command.idempotencyKey(),
                createdAt
        );

        try {
            RefundReservationResult result = refundReservationPort.reserve(newRefund);

            if (result instanceof RefundReservationResult.Created created) {
                return created.refund();
            } else if (result instanceof RefundReservationResult.Existing existing) {
                validateIdempotencyPayload(existing.refund(), command);
                return existing.refund();
            } else if (result instanceof RefundReservationResult.InsufficientCapacity insufficient) {
                throw new RefundAmountExceedsAvailableException(
                        "Requested refund amount " + command.amount() + " exceeds available capacity " + insufficient.availableAmount()
                );
            } else {
                throw new IllegalStateException("Unknown reservation result: " + result);
            }
        } catch (DuplicateRefundIdempotencyKeyException ex) {
            Refund concurrentWinner = refundRepositoryPort
                    .findByMerchantIdAndIdempotencyKey(command.merchantId(), command.idempotencyKey())
                    .orElseThrow(() -> ex);

            validateIdempotencyPayload(concurrentWinner, command);
            return concurrentWinner;
        }
    }

    private void validateIdempotencyPayload(Refund refund, CreateRefundCommand command) {
        boolean matches = refund.getPaymentId().equals(command.paymentId())
                && refund.getAmount() == command.amount();

        if (!matches) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used with different refund parameters"
            );
        }
    }
}
