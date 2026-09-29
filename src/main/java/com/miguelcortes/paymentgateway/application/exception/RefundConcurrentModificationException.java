package com.miguelcortes.paymentgateway.application.exception;

public class RefundConcurrentModificationException extends RuntimeException {

    public RefundConcurrentModificationException(String message) {
        super(message);
    }

    public RefundConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
