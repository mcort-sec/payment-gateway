package com.miguelcortes.paymentgateway.entrypoint.rest.dto;

import jakarta.validation.constraints.Positive;

public record CreateRefundRequest(
        @Positive(message = "Amount must be greater than 0")
        long amount
) {
}
