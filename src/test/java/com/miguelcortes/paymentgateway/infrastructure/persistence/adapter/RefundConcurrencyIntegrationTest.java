package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.command.CreateRefundCommand;
import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.RefundAmountExceedsAvailableException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.RefundReservationPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.application.usecase.CreateRefundUseCase;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.generator.UuidGenerator;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataRefundRepository;
import com.miguelcortes.paymentgateway.infrastructure.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
class RefundConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private RefundReservationPersistenceAdapter realReservationAdapter;

    @Autowired
    private RefundPersistenceAdapter realRefundAdapter;

    @Autowired
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Autowired
    private SpringDataRefundRepository springDataRefundRepository;

    @Autowired
    private SpringDataPaymentRepository springDataPaymentRepository;

    @Autowired
    private SpringDataMerchantRepository springDataMerchantRepository;

    private final IdGenerator idGenerator = new UuidGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();

    @BeforeEach
    void setUp() {
        springDataRefundRepository.deleteAll();
        springDataPaymentRepository.deleteAll();
        springDataMerchantRepository.deleteAll();
    }

    private Payment createApprovedPayment(UUID merchantId, UUID paymentId, long amount) {
        Merchant merchant = new Merchant(merchantId, "Merchant Conc", "conc_" + merchantId + "@test.com", Instant.now());
        merchantAdapter.save(merchant);

        Payment payment = new Payment(
                paymentId,
                merchantId,
                amount,
                Currency.COP,
                "pay-conc-" + paymentId,
                Instant.now()
        );
        payment.approve();
        paymentAdapter.save(payment);
        return payment;
    }

    @Test
    @DisplayName("A. Capacity concurrency: Payment=100k, concurrent refunds 70k and 60k -> exactly 1 succeeds, 1 fails with RefundAmountExceedsAvailableException, SUM <= 100k")
    void shouldPreventOverRefundingWhenTwoThreadsConcurrentlyRequestRefundsExceedingCapacity() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        CreateRefundCommand command1 = new CreateRefundCommand(paymentId, merchantId, 70000L, "conc-cap-key-1");
        CreateRefundCommand command2 = new CreateRefundCommand(paymentId, merchantId, 60000L, "conc-cap-key-2");

        // Use barrier to synchronize both threads just before calling reservation adapter
        ConcurrentBarrierReservationDecorator barrierReservationAdapter =
                new ConcurrentBarrierReservationDecorator(realReservationAdapter, 2);

        CreateRefundUseCase useCase = new CreateRefundUseCase(
                paymentAdapter,
                realRefundAdapter,
                barrierReservationAdapter,
                idGenerator,
                timeProvider
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Refund> future1 = executor.submit(() -> useCase.execute(command1));
            Future<Refund> future2 = executor.submit(() -> useCase.execute(command2));

            List<Future<Refund>> futures = List.of(future1, future2);
            int successCount = 0;
            int capacityConflictCount = 0;
            Refund winnerRefund = null;

            for (Future<Refund> future : futures) {
                try {
                    Refund refund = future.get(5, TimeUnit.SECONDS);
                    successCount++;
                    winnerRefund = refund;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RefundAmountExceedsAvailableException) {
                        capacityConflictCount++;
                    } else {
                        throw e;
                    }
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(capacityConflictCount).isEqualTo(1);
            assertNotNull(winnerRefund);

            long totalReserved = springDataRefundRepository.sumAmountByPaymentIdAndStatusIn(
                    paymentId,
                    List.of(RefundStatus.PENDING, RefundStatus.APPROVED)
            );
            assertThat(totalReserved).isLessThanOrEqualTo(100000L);
            assertThat(totalReserved).isEqualTo(winnerRefund.getAmount());
            assertThat(springDataRefundRepository.count()).isEqualTo(1L);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("B. Same payment + same idempotency: both threads obtain semantically same Refund and exactly 1 row is persisted")
    void shouldHandleConcurrentSamePaymentAndSameIdempotencyKey() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentId, 100000L);

        String idempotencyKey = "conc-same-payment-key";
        CreateRefundCommand command1 = new CreateRefundCommand(paymentId, merchantId, 50000L, idempotencyKey);
        CreateRefundCommand command2 = new CreateRefundCommand(paymentId, merchantId, 50000L, idempotencyKey);

        ConcurrentBarrierReservationDecorator barrierReservationAdapter =
                new ConcurrentBarrierReservationDecorator(realReservationAdapter, 2);

        CreateRefundUseCase useCase = new CreateRefundUseCase(
                paymentAdapter,
                realRefundAdapter,
                barrierReservationAdapter,
                idGenerator,
                timeProvider
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Refund> future1 = executor.submit(() -> useCase.execute(command1));
            Future<Refund> future2 = executor.submit(() -> useCase.execute(command2));

            Refund refund1 = future1.get(5, TimeUnit.SECONDS);
            Refund refund2 = future2.get(5, TimeUnit.SECONDS);

            assertThat(refund1).isNotNull();
            assertThat(refund2).isNotNull();
            assertEquals(refund1.getId(), refund2.getId());
            assertEquals(50000L, refund1.getAmount());
            assertEquals(50000L, refund2.getAmount());

            assertThat(springDataRefundRepository.count()).isEqualTo(1L);
            Optional<RefundEntity> entity = springDataRefundRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            assertThat(entity).isPresent();
            assertEquals(refund1.getId(), entity.get().getId());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("C. Different payments + same idempotency: 1 winner, 1 IdempotencyConflictException, recovery after transaction rollback")
    void shouldHandleConcurrentDifferentPaymentsWithSameIdempotencyKeyAndRecoverAfterRollback() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        createApprovedPayment(merchantId, paymentA, 100000L);
        createApprovedPayment(merchantId, paymentB, 100000L);

        String sharedKey = "cross-payment-race-key";
        CreateRefundCommand commandA = new CreateRefundCommand(paymentA, merchantId, 40000L, sharedKey);
        CreateRefundCommand commandB = new CreateRefundCommand(paymentB, merchantId, 60000L, sharedKey);

        ConcurrentBarrierReservationDecorator barrierReservationAdapter =
                new ConcurrentBarrierReservationDecorator(realReservationAdapter, 2);

        CreateRefundUseCase useCase = new CreateRefundUseCase(
                paymentAdapter,
                realRefundAdapter,
                barrierReservationAdapter,
                idGenerator,
                timeProvider
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Refund> futureA = executor.submit(() -> useCase.execute(commandA));
            Future<Refund> futureB = executor.submit(() -> useCase.execute(commandB));

            List<Future<Refund>> futures = List.of(futureA, futureB);
            int successCount = 0;
            int conflictCount = 0;
            Refund successfulRefund = null;

            for (Future<Refund> future : futures) {
                try {
                    Refund refund = future.get(5, TimeUnit.SECONDS);
                    successCount++;
                    successfulRefund = refund;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IdempotencyConflictException) {
                        conflictCount++;
                    } else {
                        throw e;
                    }
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertNotNull(successfulRefund);

            assertThat(springDataRefundRepository.count()).isEqualTo(1L);
            Optional<RefundEntity> persisted = springDataRefundRepository.findByMerchantIdAndIdempotencyKey(merchantId, sharedKey);
            assertThat(persisted).isPresent();
            assertEquals(successfulRefund.getId(), persisted.get().getId());
            assertEquals(successfulRefund.getPaymentId(), persisted.get().getPaymentId());
            assertEquals(successfulRefund.getAmount(), persisted.get().getAmount());
        } finally {
            executor.shutdown();
        }
    }

    private static class ConcurrentBarrierReservationDecorator implements RefundReservationPort {

        private final RefundReservationPort delegate;
        private final AtomicInteger callCounter = new AtomicInteger(0);
        private final CyclicBarrier barrier;

        ConcurrentBarrierReservationDecorator(RefundReservationPort delegate, int parties) {
            this.delegate = delegate;
            this.barrier = new CyclicBarrier(parties);
        }

        @Override
        public RefundReservationResult reserve(Refund refund) {
            int call = callCounter.incrementAndGet();
            if (call <= 2) {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted waiting at barrier", e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException("Barrier wait failed or timed out", e);
                }
            }

            return delegate.reserve(refund);
        }
    }
}
