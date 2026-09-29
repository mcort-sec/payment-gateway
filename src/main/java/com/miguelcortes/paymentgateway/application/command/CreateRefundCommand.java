package com.miguelcortes.paymentgateway.application.command;

import java.util.UUID;

public record CreateRefundCommand(
        UUID paymentId,
        UUID merchantId,
        long amount,
        String idempotencyKey
) {
}
