package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

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
        springDataRepository.save(entity);
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
}
