package com.miguelcortes.paymentgateway.application.exception;

public class DuplicateMerchantEmailException extends RuntimeException {
    public DuplicateMerchantEmailException(String message) {
        super(message);
    }

    public DuplicateMerchantEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
