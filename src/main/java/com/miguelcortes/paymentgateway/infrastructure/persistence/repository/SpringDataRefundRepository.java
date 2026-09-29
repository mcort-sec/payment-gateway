package com.miguelcortes.paymentgateway.infrastructure.persistence.repository;

import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRefundRepository extends JpaRepository<RefundEntity, UUID> {

    Optional<RefundEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<RefundEntity> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    Page<RefundEntity> findByMerchantId(UUID merchantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r WHERE r.paymentId = :paymentId AND r.status IN :statuses")
    long sumAmountByPaymentIdAndStatusIn(
            @Param("paymentId") UUID paymentId,
            @Param("statuses") Collection<RefundStatus> statuses
    );
}
