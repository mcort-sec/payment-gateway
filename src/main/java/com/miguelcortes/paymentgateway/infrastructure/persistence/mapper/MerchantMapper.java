package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.stereotype.Component;

@Component
public class MerchantMapper {

    public MerchantEntity toEntity(Merchant domain) {
        if (domain == null) {
            return null;
        }
        return new MerchantEntity(
                domain.getId(),
                domain.getName(),
                domain.getEmail(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }

    public Merchant toDomain(MerchantEntity entity) {
        if (entity == null) {
            return null;
        }
        return Merchant.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
