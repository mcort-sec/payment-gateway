package com.miguelcortes.paymentgateway.domain.exception;

public class InvalidProcessorException extends RuntimeException {
    public InvalidProcessorException(String message) {
        super(message);
    }
}
