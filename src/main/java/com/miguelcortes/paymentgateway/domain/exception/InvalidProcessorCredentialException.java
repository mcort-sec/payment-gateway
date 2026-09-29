package com.miguelcortes.paymentgateway.domain.exception;

public class InvalidProcessorCredentialException extends RuntimeException {
    public InvalidProcessorCredentialException(String message) {
        super(message);
    }
}
