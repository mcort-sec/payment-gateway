package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataProcessorRepository extends JpaRepository<ProcessorEntity, UUID> {
}
