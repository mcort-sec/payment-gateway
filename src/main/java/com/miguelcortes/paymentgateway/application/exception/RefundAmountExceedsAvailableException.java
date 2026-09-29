package com.miguelcortes.paymentgateway.application.exception;

public class RefundAmountExceedsAvailableException extends RuntimeException {

    public RefundAmountExceedsAvailableException(String message) {
        super(message);
    }

    public RefundAmountExceedsAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
