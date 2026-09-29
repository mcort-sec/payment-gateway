package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.MerchantNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.MerchantSuspendedException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class CreatePaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final MerchantRepositoryPort merchantRepositoryPort;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public CreatePaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            MerchantRepositoryPort merchantRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Payment execute(CreatePaymentCommand command) {
        Merchant merchant = merchantRepositoryPort.findById(command.merchantId())
                .orElseThrow(() -> new MerchantNotFoundException(command.merchantId()));

        Optional<Payment> existingPayment = paymentRepositoryPort
                .findByMerchantIdAndIdempotencyKey(command.merchantId(), command.idempotencyKey());

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            validateIdempotencyPayload(payment, command);
            return payment;
        }

        if (!merchant.isActive()) {
            throw new MerchantSuspendedException(command.merchantId());
        }

        UUID id = idGenerator.generate();
        Instant createdAt = timeProvider.now();

        Payment newPayment = new Payment(
                id,
                command.merchantId(),
                command.amount(),
                command.currency(),
                command.idempotencyKey(),
                createdAt
        );

        try {
            paymentRepositoryPort.save(newPayment);
            return newPayment;
        } catch (DuplicateIdempotencyKeyException ex) {
            Payment concurrentWinner = paymentRepositoryPort
                    .findByMerchantIdAndIdempotencyKey(command.merchantId(), command.idempotencyKey())
                    .orElseThrow(() -> ex);

            validateIdempotencyPayload(concurrentWinner, command);
            return concurrentWinner;
        }
    }

    private void validateIdempotencyPayload(Payment payment, CreatePaymentCommand command) {
        boolean matches = payment.getAmount() == command.amount()
                && payment.getCurrency() == command.currency();

        if (!matches) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used with different payment parameters"
            );
        }
    }
}
