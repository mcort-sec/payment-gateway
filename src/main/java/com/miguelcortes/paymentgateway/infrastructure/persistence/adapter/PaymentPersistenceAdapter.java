package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.PaymentConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_payments_customer_idempotency";

    private final SpringDataPaymentRepository springDataRepository;
    private final PaymentMapper paymentMapper;

    public PaymentPersistenceAdapter(
            SpringDataPaymentRepository springDataRepository,
            PaymentMapper paymentMapper
    ) {
        this.springDataRepository = springDataRepository;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public void save(Payment payment) {
        PaymentEntity entity = paymentMapper.toEntity(payment);
        try {
            springDataRepository.saveAndFlush(entity);
        } catch (OptimisticLockingFailureException ex) {
            throw new PaymentConcurrentModificationException(
                    "Payment with id " + payment.getId() + " was modified concurrently by another transaction",
                    ex
            );
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyConstraintViolation(ex)) {
                throw new DuplicateIdempotencyKeyException(
                        "Duplicate payment request for customer and idempotency key: " + payment.getIdempotencyKey(),
                        ex
                );
            }
            throw ex;
        }
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return springDataRepository.findById(id)
                .map(paymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey) {
        return springDataRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(paymentMapper::toDomain);
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
