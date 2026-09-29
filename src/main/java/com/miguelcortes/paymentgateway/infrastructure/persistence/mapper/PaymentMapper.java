package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentEntity toEntity(Payment domain) {
        if (domain == null) {
            return null;
        }
        return new PaymentEntity(
                domain.getId(),
                domain.getMerchantId(),
                domain.getAmount(),
                domain.getCurrency(),
                domain.getStatus(),
                domain.getIdempotencyKey(),
                domain.getCreatedAt(),
                domain.getVersion()
        );
    }

    public Payment toDomain(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }
        return Payment.reconstitute(
                entity.getId(),
                entity.getMerchantId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getCreatedAt(),
                entity.getVersion()
        );
    }
}
