package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ProcessorCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataProcessorCredentialRepository extends JpaRepository<ProcessorCredentialEntity, UUID> {

    Optional<ProcessorCredentialEntity> findByKeyPrefix(String keyPrefix);
}
