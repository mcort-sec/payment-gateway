package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataRefundRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<RefundEntity> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}
