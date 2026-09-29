package com.miguelcortes.paymentgateway.application.exception;

public class ApiCredentialGenerationException extends RuntimeException {

    public ApiCredentialGenerationException(String message) {
        super(message);
    }

    public ApiCredentialGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
