package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.ApiCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataApiCredentialRepository extends JpaRepository<ApiCredentialEntity, UUID> {

    Optional<ApiCredentialEntity> findByKeyPrefix(String keyPrefix);
}
