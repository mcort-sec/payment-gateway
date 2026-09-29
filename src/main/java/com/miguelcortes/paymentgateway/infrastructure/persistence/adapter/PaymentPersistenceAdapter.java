package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.PaymentConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private static final String IDEMPOTENCY_CONSTRAINT = "uq_payments_merchant_idempotency";

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
                        "Duplicate payment request for merchant and idempotency key: " + payment.getIdempotencyKey(),
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
    public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
        return springDataRepository.findByIdAndMerchantId(id, merchantId)
                .map(paymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
        return springDataRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .map(paymentMapper::toDomain);
    }

    @Override
    public PageResult<Payment> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
        Pageable pageable = PageRequest.of(
                pageQuery.page(),
                pageQuery.size(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<PaymentEntity> page = springDataRepository.findByMerchantId(merchantId, pageable);
        List<Payment> items = page.getContent().stream()
                .map(paymentMapper::toDomain)
                .toList();

        return new PageResult<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
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
