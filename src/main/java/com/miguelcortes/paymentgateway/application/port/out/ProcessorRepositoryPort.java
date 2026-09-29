package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.domain.model.Processor;

import java.util.Optional;
import java.util.UUID;

public interface ProcessorRepositoryPort {

    void save(Processor processor);

    Optional<Processor> findById(UUID id);
}
