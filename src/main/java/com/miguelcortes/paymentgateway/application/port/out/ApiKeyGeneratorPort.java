package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;

public interface ApiKeyGeneratorPort {

    GeneratedApiKey generateTestKey();
}
