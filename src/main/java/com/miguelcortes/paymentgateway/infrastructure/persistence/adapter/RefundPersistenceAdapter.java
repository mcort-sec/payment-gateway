package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateRefundIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.RefundConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.RefundMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataRefundRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RefundPersistenceAdapter implements RefundRepositoryPort {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_refunds_merchant_idempotency";

    private final SpringDataRefundRepository springDataRepository;
    private final RefundMapper refundMapper;

    public RefundPersistenceAdapter(
            SpringDataRefundRepository springDataRepository,
            RefundMapper refundMapper
    ) {
        this.springDataRepository = springDataRepository;
        this.refundMapper = refundMapper;
    }

    @Override
    public void save(Refund refund) {
        RefundEntity entity = refundMapper.toEntity(refund);
        try {
            springDataRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException ex) {
            throw new RefundConcurrentModificationException(
                    "Refund with id " + refund.getId() + " was modified concurrently by another transaction",
                    ex
            );
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyConstraintViolation(ex)) {
                throw new DuplicateRefundIdempotencyKeyException(
                        "Duplicate refund request for merchant and idempotency key: " + refund.getIdempotencyKey(),
                        ex
                );
            }
            throw ex;
        }
    }

    @Override
    public Optional<Refund> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(refundMapper::toDomain);
    }

    @Override
    public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
        return springDataRepository.findByIdAndMerchantId(id, merchantId)
                .map(refundMapper::toDomain);
    }

    @Override
    public Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
        return springDataRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .map(refundMapper::toDomain);
    }

    private boolean isIdempotencyConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                if (IDEMPOTENCY_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                    return true;
                }
            }
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains(IDEMPOTENCY_CONSTRAINT);
    }
}
