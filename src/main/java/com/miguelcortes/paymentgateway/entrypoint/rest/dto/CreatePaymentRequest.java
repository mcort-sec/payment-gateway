package com.miguelcortes.paymentgateway.entrypoint.rest.dto;

import com.miguelcortes.paymentgateway.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "Merchant ID is required")
        UUID merchantId,

        @Positive(message = "Amount must be greater than 0")
        long amount,

        @NotNull(message = "Currency is required")
        Currency currency
) {
}
