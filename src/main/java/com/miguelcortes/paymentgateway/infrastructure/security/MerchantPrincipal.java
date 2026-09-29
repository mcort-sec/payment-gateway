package com.miguelcortes.paymentgateway.infrastructure.security;

import java.util.Objects;
import java.util.UUID;

public record MerchantPrincipal(UUID merchantId) {
    public MerchantPrincipal {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
    }
}
