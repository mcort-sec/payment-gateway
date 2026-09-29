package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;

import java.util.Optional;
import java.util.UUID;

public interface ProcessorCredentialRepositoryPort {

    void save(ProcessorCredential credential);

    Optional<ProcessorCredential> findById(UUID id);

    Optional<ProcessorCredential> findByKeyPrefix(String keyPrefix);
}
