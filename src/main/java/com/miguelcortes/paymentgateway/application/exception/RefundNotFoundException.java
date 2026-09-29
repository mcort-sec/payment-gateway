package com.miguelcortes.paymentgateway.application.exception;

import java.util.UUID;

public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(UUID id) {
        super("Refund not found with id: " + id);
    }
}
