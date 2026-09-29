package com.miguelcortes.paymentgateway.entrypoint.rest.dto;

import com.miguelcortes.paymentgateway.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @Positive(message = "Amount must be greater than 0")
        long amount,

        @NotNull(message = "Currency is required")
        Currency currency
) {
}
