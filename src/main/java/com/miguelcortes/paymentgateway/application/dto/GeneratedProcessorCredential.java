package com.miguelcortes.paymentgateway.application.dto;

import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;

public record GeneratedProcessorCredential(
        ProcessorCredential credential,
        String plaintextApiKey
) {}
