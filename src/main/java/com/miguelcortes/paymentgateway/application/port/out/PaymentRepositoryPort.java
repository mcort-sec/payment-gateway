package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {

    void save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}
