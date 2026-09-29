package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}
