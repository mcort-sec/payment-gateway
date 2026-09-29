package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ApiCredentialEntity;
import org.springframework.stereotype.Component;

@Component
public class ApiCredentialMapper {

    public ApiCredentialEntity toEntity(ApiCredential domain) {
        if (domain == null) {
            return null;
        }
        return new ApiCredentialEntity(
                domain.getId(),
                domain.getMerchantId(),
                domain.getKeyPrefix(),
                domain.getKeyHash(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getRevokedAt()
        );
    }

    public ApiCredential toDomain(ApiCredentialEntity entity) {
        if (entity == null) {
            return null;
        }
        return ApiCredential.reconstitute(
                entity.getId(),
                entity.getMerchantId(),
                entity.getKeyPrefix(),
                entity.getKeyHash(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getRevokedAt()
        );
    }
}
