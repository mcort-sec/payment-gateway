package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.PaymentMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.RefundMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        RefundReservationPersistenceAdapter.class,
        RefundPersistenceAdapter.class,
        RefundMapper.class,
        PaymentPersistenceAdapter.class,
        PaymentMapper.class,
        MerchantPersistenceAdapter.class,
        MerchantMapper.class
})
@Testcontainers
class RefundReservationPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private RefundReservationPersistenceAdapter reservationAdapter;

    @Autowired
    private RefundPersistenceAdapter refundAdapter;

    @Autowired
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    private Payment createApprovedPayment(UUID merchantId, UUID paymentId, long amount) {
        Merchant merchant = new Merchant(merchantId, "Merchant Reservation", "res_" + merchantId + "@test.com", Instant.now());
        merchantAdapter.save(merchant);

        Payment payment = new Payment(
                paymentId,
                merchantId,
                amount,
                Currency.COP,
                "pay-res-" + paymentId,
                Instant.now()
        );
        payment.approve();
        paymentAdapter.save(payment);
        return payment;
    }

    @Test
    @DisplayName("Should count PENDING refund as reserved capacity")
    void shouldCountPendingRefundAsReservedCapacity() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        // Pre-create PENDING refund of 40000
        Refund pendingRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                40000L,
                Currency.COP,
                "ref-pending-existing",
                Instant.now()
        );
        refundAdapter.save(pendingRefund);

        // Try to reserve 70000 (100000 - 40000 = 60000 available < 70000)
        Refund newRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                70000L,
                Currency.COP,
                "ref-try-70",
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(newRefund);
        assertInstanceOf(RefundReservationResult.InsufficientCapacity.class, result);
        RefundReservationResult.InsufficientCapacity insufficient = (RefundReservationResult.InsufficientCapacity) result;
        assertEquals(60000L, insufficient.availableAmount());
    }

    @Test
    @DisplayName("Should count APPROVED refund as reserved capacity")
    void shouldCountApprovedRefundAsReservedCapacity() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        // Pre-create APPROVED refund of 50000
        Refund approvedRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                "ref-approved-existing",
                Instant.now()
        );
        approvedRefund.approve();
        refundAdapter.save(approvedRefund);

        // Try to reserve 60000 (100000 - 50000 = 50000 available < 60000)
        Refund newRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                60000L,
                Currency.COP,
                "ref-try-60",
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(newRefund);
        assertInstanceOf(RefundReservationResult.InsufficientCapacity.class, result);
        RefundReservationResult.InsufficientCapacity insufficient = (RefundReservationResult.InsufficientCapacity) result;
        assertEquals(50000L, insufficient.availableAmount());
    }

    @Test
    @DisplayName("Should NOT count DECLINED refund as reserved capacity")
    void shouldNotCountDeclinedRefundAsReservedCapacity() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        // Pre-create DECLINED refund of 50000
        Refund declinedRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                "ref-declined-existing",
                Instant.now()
        );
        declinedRefund.decline();
        refundAdapter.save(declinedRefund);

        // Try to reserve 80000 (100000 available since DECLINED releases capacity)
        Refund newRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                80000L,
                Currency.COP,
                "ref-try-80",
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(newRefund);
        assertInstanceOf(RefundReservationResult.Created.class, result);
        RefundReservationResult.Created created = (RefundReservationResult.Created) result;
        assertEquals(80000L, created.refund().getAmount());
    }

    @Test
    @DisplayName("Should successfully reserve full available amount")
    void shouldReserveFullAvailableAmount() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        Refund fullRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                100000L,
                Currency.COP,
                "ref-full-res",
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(fullRefund);
        assertInstanceOf(RefundReservationResult.Created.class, result);
        RefundReservationResult.Created created = (RefundReservationResult.Created) result;
        assertEquals(100000L, created.refund().getAmount());
        assertNotNull(refundAdapter.findById(fullRefund.getId()));
    }

    @Test
    @DisplayName("Should return InsufficientCapacity when amount exceeds total payment amount with zero existing refunds")
    void shouldReturnInsufficientCapacityWhenExceedsTotalPayment() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 50000L);

        Refund excessRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                60000L,
                Currency.COP,
                "ref-excess",
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(excessRefund);
        assertInstanceOf(RefundReservationResult.InsufficientCapacity.class, result);
        RefundReservationResult.InsufficientCapacity insufficient = (RefundReservationResult.InsufficientCapacity) result;
        assertEquals(50000L, insufficient.availableAmount());
    }

    @Test
    @DisplayName("Should detect Existing refund inside lock before capacity check")
    void shouldDetectExistingRefundInsideLockBeforeCapacityCheck() {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        String idempotencyKey = "ref-existing-key-1";

        // Pre-create refund
        Refund existingRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                40000L,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );
        refundAdapter.save(existingRefund);

        // Attempt reservation with same idempotency key
        Refund recheckRefund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                40000L,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );

        RefundReservationResult result = reservationAdapter.reserve(recheckRefund);
        assertInstanceOf(RefundReservationResult.Existing.class, result);
        RefundReservationResult.Existing existingResult = (RefundReservationResult.Existing) result;
        assertEquals(existingRefund.getId(), existingResult.refund().getId());
        assertEquals(40000L, existingResult.refund().getAmount());
    }
}
