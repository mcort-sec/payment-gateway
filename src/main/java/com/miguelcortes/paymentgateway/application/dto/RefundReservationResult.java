package com.miguelcortes.paymentgateway.application.dto;

import com.miguelcortes.paymentgateway.domain.model.Refund;

public sealed interface RefundReservationResult {

    record Created(Refund refund) implements RefundReservationResult {
    }

    record Existing(Refund refund) implements RefundReservationResult {
    }

    record InsufficientCapacity(long availableAmount) implements RefundReservationResult {
    }
}
