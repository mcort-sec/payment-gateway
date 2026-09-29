package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.model.ApiKeyType;

public interface ApiKeyGeneratorPort {

    GeneratedApiKey generate(ApiKeyType type);
}
