package com.miguelcortes.paymentgateway.domain.exception;

public class InvalidMerchantException extends RuntimeException {
    public InvalidMerchantException(String message) {
        super(message);
    }
}
