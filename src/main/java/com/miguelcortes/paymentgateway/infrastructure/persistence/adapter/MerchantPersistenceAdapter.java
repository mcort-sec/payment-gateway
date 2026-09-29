package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateMerchantEmailException;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.MerchantEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class MerchantPersistenceAdapter implements MerchantRepositoryPort {

    private static final String UNIQUE_EMAIL_CONSTRAINT = "uq_merchants_email";

    private final SpringDataMerchantRepository repository;
    private final MerchantMapper mapper;

    public MerchantPersistenceAdapter(
            SpringDataMerchantRepository repository,
            MerchantMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(Merchant merchant) {
        MerchantEntity entity = mapper.toEntity(merchant);
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueEmailConstraintViolation(ex)) {
                throw new DuplicateMerchantEmailException(
                        "Merchant with email " + merchant.getEmail() + " already exists",
                        ex
                );
            }
            throw ex;
        }
    }

    @Override
    public Optional<Merchant> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Merchant> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    private boolean isUniqueEmailConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                if (UNIQUE_EMAIL_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                    return true;
                }
            }
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains(UNIQUE_EMAIL_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains(UNIQUE_EMAIL_CONSTRAINT);
    }
}
