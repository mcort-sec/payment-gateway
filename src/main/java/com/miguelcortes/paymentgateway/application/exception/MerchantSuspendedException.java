package com.miguelcortes.paymentgateway.application.exception;

import java.util.UUID;

public class MerchantSuspendedException extends RuntimeException {

    private final UUID merchantId;

    public MerchantSuspendedException(UUID merchantId) {
        super("Merchant is suspended: " + merchantId);
        this.merchantId = merchantId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}
