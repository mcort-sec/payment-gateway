package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataMerchantRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findByEmail(String email);
}
