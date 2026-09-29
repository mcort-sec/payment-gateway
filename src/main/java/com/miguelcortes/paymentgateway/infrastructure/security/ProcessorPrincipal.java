package com.miguelcortes.paymentgateway.infrastructure.security;

import java.util.Objects;
import java.util.UUID;

public record ProcessorPrincipal(UUID processorId) {
    public ProcessorPrincipal {
        Objects.requireNonNull(processorId, "processorId must not be null");
    }
}
