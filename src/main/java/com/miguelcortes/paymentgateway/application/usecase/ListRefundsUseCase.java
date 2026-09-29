package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Refund;

import java.util.UUID;

public class ListRefundsUseCase {

    private final RefundRepositoryPort refundRepositoryPort;

    public ListRefundsUseCase(RefundRepositoryPort refundRepositoryPort) {
        this.refundRepositoryPort = refundRepositoryPort;
    }

    public PageResult<Refund> execute(UUID requesterMerchantId, PageQuery pageQuery) {
        if (requesterMerchantId == null) {
            throw new IllegalArgumentException("Requester merchant ID must not be null");
        }
        if (pageQuery == null) {
            throw new IllegalArgumentException("Page query must not be null");
        }
        return refundRepositoryPort.findByMerchantId(requesterMerchantId, pageQuery);
    }
}
