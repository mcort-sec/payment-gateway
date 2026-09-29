package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorCredentialEntity;
import org.springframework.stereotype.Component;

@Component
public class ProcessorCredentialMapper {

    public ProcessorCredentialEntity toEntity(ProcessorCredential domain) {
        if (domain == null) {
            return null;
        }
        return new ProcessorCredentialEntity(
                domain.getId(),
                domain.getProcessorId(),
                domain.getKeyPrefix(),
                domain.getKeyHash(),
                domain.getStatus(),
                domain.getCreatedAt(),
                domain.getRevokedAt()
        );
    }

    public ProcessorCredential toDomain(ProcessorCredentialEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProcessorCredential.reconstitute(
                entity.getId(),
                entity.getProcessorId(),
                entity.getKeyPrefix(),
                entity.getKeyHash(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getRevokedAt()
        );
    }
}
