package com.miguelcortes.paymentgateway.domain.exception;

public class InvalidApiCredentialException extends RuntimeException {

    public InvalidApiCredentialException(String message) {
        super(message);
    }
}
