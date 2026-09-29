package com.miguelcortes.paymentgateway.application.exception;

public class PaymentConcurrentModificationException extends RuntimeException {

    public PaymentConcurrentModificationException(String message) {
        super(message);
    }

    public PaymentConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
