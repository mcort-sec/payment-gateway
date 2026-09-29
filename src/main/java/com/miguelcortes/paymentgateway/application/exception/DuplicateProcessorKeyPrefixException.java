package com.miguelcortes.paymentgateway.application.exception;

public class DuplicateProcessorKeyPrefixException extends RuntimeException {

    public DuplicateProcessorKeyPrefixException(String message) {
        super(message);
    }

    public DuplicateProcessorKeyPrefixException(String message, Throwable cause) {
        super(message, cause);
    }
}
