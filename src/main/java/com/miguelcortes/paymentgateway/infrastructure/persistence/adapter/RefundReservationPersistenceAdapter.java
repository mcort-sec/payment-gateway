package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundReservationPort;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.RefundMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataRefundRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class RefundReservationPersistenceAdapter implements RefundReservationPort {

    private final SpringDataPaymentRepository springDataPaymentRepository;
    private final SpringDataRefundRepository springDataRefundRepository;
    private final RefundRepositoryPort refundRepositoryPort;
    private final RefundMapper refundMapper;

    public RefundReservationPersistenceAdapter(
            SpringDataPaymentRepository springDataPaymentRepository,
            SpringDataRefundRepository springDataRefundRepository,
            RefundRepositoryPort refundRepositoryPort,
            RefundMapper refundMapper
    ) {
        this.springDataPaymentRepository = springDataPaymentRepository;
        this.springDataRefundRepository = springDataRefundRepository;
        this.refundRepositoryPort = refundRepositoryPort;
        this.refundMapper = refundMapper;
    }

    @Override
    @Transactional
    public RefundReservationResult reserve(Refund refund) {
        PaymentEntity lockedPayment = springDataPaymentRepository.findByIdForUpdate(refund.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment with id " + refund.getPaymentId() + " not found during reservation lock"
                ));

        long paymentLimit = lockedPayment.getAmount();

        Optional<RefundEntity> existingEntity = springDataRefundRepository.findByMerchantIdAndIdempotencyKey(
                refund.getMerchantId(),
                refund.getIdempotencyKey()
        );

        if (existingEntity.isPresent()) {
            Refund existingRefund = refundMapper.toDomain(existingEntity.get());
            return new RefundReservationResult.Existing(existingRefund);
        }

        long reservedAmount = springDataRefundRepository.sumAmountByPaymentIdAndStatusIn(
                refund.getPaymentId(),
                List.of(RefundStatus.PENDING, RefundStatus.APPROVED)
        );

        long availableAmount = paymentLimit - reservedAmount;

        if (refund.getAmount() > availableAmount) {
            return new RefundReservationResult.InsufficientCapacity(availableAmount);
        }

        refundRepositoryPort.save(refund);

        return new RefundReservationResult.Created(refund);
    }
}
