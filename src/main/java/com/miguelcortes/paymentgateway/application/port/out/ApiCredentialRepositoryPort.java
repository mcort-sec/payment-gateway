package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.domain.model.ApiCredential;

import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepositoryPort {

    void save(ApiCredential credential);

    Optional<ApiCredential> findById(UUID id);

    Optional<ApiCredential> findByKeyPrefix(String keyPrefix);
}
