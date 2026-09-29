package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorEntity;
import org.springframework.stereotype.Component;

@Component
public class ProcessorMapper {

    public ProcessorEntity toEntity(Processor domain) {
        if (domain == null) {
            return null;
        }
        return new ProcessorEntity(
                domain.getId(),
                domain.getName(),
                domain.getStatus(),
                domain.getCreatedAt()
        );
    }

    public Processor toDomain(ProcessorEntity entity) {
        if (entity == null) {
            return null;
        }
        return Processor.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
