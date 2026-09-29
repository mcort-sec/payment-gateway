package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ProcessorMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProcessorPersistenceAdapter implements ProcessorRepositoryPort {

    private final SpringDataProcessorRepository repository;
    private final ProcessorMapper mapper;

    public ProcessorPersistenceAdapter(
            SpringDataProcessorRepository repository,
            ProcessorMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(Processor processor) {
        ProcessorEntity entity = mapper.toEntity(processor);
        repository.saveAndFlush(entity);
    }

    @Override
    public Optional<Processor> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
