package com.miguelcortes.paymentgateway.application.command;

import com.miguelcortes.paymentgateway.domain.model.Currency;

import java.util.UUID;

public record CreatePaymentCommand(
        UUID merchantId,
        long amount,
        Currency currency,
        String idempotencyKey
) {
}
