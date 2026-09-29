package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateProcessorKeyPrefixException;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorCredentialEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ProcessorCredentialMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorCredentialRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProcessorCredentialPersistenceAdapter implements ProcessorCredentialRepositoryPort {

    private static final String UNIQUE_PREFIX_CONSTRAINT = "uq_processor_credentials_key_prefix";

    private final SpringDataProcessorCredentialRepository springDataRepository;
    private final ProcessorCredentialMapper mapper;

    public ProcessorCredentialPersistenceAdapter(
            SpringDataProcessorCredentialRepository springDataRepository,
            ProcessorCredentialMapper mapper
    ) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(ProcessorCredential credential) {
        ProcessorCredentialEntity entity = mapper.toEntity(credential);
        try {
            springDataRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            if (isKeyPrefixConstraintViolation(ex)) {
                throw new DuplicateProcessorKeyPrefixException(
                        "Duplicate processor key prefix violation: " + credential.getKeyPrefix(),
                        ex
                );
            }
            throw ex;
        }
    }

    @Override
    public Optional<ProcessorCredential> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ProcessorCredential> findByKeyPrefix(String keyPrefix) {
        return springDataRepository.findByKeyPrefix(keyPrefix)
                .map(mapper::toDomain);
    }

    private boolean isKeyPrefixConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                if (UNIQUE_PREFIX_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                    return true;
                }
            }
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains(UNIQUE_PREFIX_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains(UNIQUE_PREFIX_CONSTRAINT);
    }
}
