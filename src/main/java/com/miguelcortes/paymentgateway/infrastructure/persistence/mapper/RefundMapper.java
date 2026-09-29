package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundEntity toEntity(Refund domain) {
        if (domain == null) {
            return null;
        }
        return new RefundEntity(
                domain.getId(),
                domain.getPaymentId(),
                domain.getMerchantId(),
                domain.getAmount(),
                domain.getCurrency(),
                domain.getStatus(),
                domain.getIdempotencyKey(),
                domain.getCreatedAt(),
                domain.getVersion()
        );
    }

    public Refund toDomain(RefundEntity entity) {
        if (entity == null) {
            return null;
        }
        return Refund.reconstitute(
                entity.getId(),
                entity.getPaymentId(),
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
