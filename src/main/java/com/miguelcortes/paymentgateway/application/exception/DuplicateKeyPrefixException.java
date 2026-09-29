package com.miguelcortes.paymentgateway.application.exception;

public class DuplicateKeyPrefixException extends RuntimeException {

    public DuplicateKeyPrefixException(String message) {
        super(message);
    }

    public DuplicateKeyPrefixException(String message, Throwable cause) {
        super(message, cause);
    }
}
