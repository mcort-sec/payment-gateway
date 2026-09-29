package com.miguelcortes.paymentgateway.application.dto;

public record GeneratedApiKey(
        String keyPrefix,
        String fullPlaintextApiKey
) {}
