package com.miguelcortes.paymentgateway.application.exception;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException {

    private final UUID merchantId;

    public MerchantNotFoundException(UUID merchantId) {
        super("Merchant not found with id: " + merchantId);
        this.merchantId = merchantId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}
