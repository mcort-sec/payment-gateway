package com.miguelcortes.paymentgateway.application.dto;

import com.miguelcortes.paymentgateway.domain.model.ApiCredential;

public record GeneratedApiCredential(
        ApiCredential credential,
        String plaintextApiKey
) {}
