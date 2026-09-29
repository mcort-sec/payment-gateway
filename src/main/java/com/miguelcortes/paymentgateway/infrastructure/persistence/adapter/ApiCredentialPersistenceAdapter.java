package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateKeyPrefixException;
import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ApiCredentialEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ApiCredentialMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataApiCredentialRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApiCredentialPersistenceAdapter implements ApiCredentialRepositoryPort {

    private static final String UNIQUE_PREFIX_CONSTRAINT = "uq_api_credentials_key_prefix";

    private final SpringDataApiCredentialRepository springDataRepository;
    private final ApiCredentialMapper mapper;

    public ApiCredentialPersistenceAdapter(
            SpringDataApiCredentialRepository springDataRepository,
            ApiCredentialMapper mapper
    ) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(ApiCredential credential) {
        ApiCredentialEntity entity = mapper.toEntity(credential);
        try {
            springDataRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            if (isKeyPrefixConstraintViolation(ex)) {
                throw new DuplicateKeyPrefixException(
                        "Duplicate key prefix violation: " + credential.getKeyPrefix(),
                        ex
                );
            }
            throw ex;
        }
    }

    @Override
    public Optional<ApiCredential> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ApiCredential> findByKeyPrefix(String keyPrefix) {
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
