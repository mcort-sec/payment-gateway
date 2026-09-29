package com.miguelcortes.paymentgateway.application.exception;

public class ProcessorCredentialGenerationException extends RuntimeException {
    public ProcessorCredentialGenerationException(String message) {
        super(message);
    }

    public ProcessorCredentialGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
