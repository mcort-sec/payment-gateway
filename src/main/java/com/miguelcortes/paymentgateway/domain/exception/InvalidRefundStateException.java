package com.miguelcortes.paymentgateway.domain.exception;

public class InvalidRefundStateException extends RuntimeException {
    public InvalidRefundStateException(String message) {
        super(message);
    }
}
