package com.miguelcortes.paymentgateway.application.command;

import com.miguelcortes.paymentgateway.domain.model.Currency;

import java.util.UUID;

public record CreatePaymentCommand(
        UUID customerId,
        long amount,
        Currency currency,
        String idempotencyKey
) {
}
