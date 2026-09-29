package com.miguelcortes.paymentgateway.application.exception;

public class DuplicateRefundIdempotencyKeyException extends RuntimeException {

    public DuplicateRefundIdempotencyKeyException(String message) {
        super(message);
    }

    public DuplicateRefundIdempotencyKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
