package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.domain.model.Refund;

import java.util.Optional;
import java.util.UUID;

public interface RefundRepositoryPort {

    void save(Refund refund);

    Optional<Refund> findById(UUID id);

    Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    PageResult<Refund> findByMerchantId(UUID merchantId, PageQuery pageQuery);
}
