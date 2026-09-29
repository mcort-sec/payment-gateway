package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Payment;

import java.util.UUID;

public class ListPaymentsUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;

    public ListPaymentsUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    public PageResult<Payment> execute(UUID requesterMerchantId, PageQuery pageQuery) {
        if (requesterMerchantId == null) {
            throw new IllegalArgumentException("Requester merchant ID must not be null");
        }
        if (pageQuery == null) {
            throw new IllegalArgumentException("Page query must not be null");
        }
        return paymentRepositoryPort.findByMerchantId(requesterMerchantId, pageQuery);
    }
}
