package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.RefundConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.application.usecase.ApproveRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclineRefundUseCase;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorCredentialRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataRefundRepository;
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
class RefundOptimisticLockingConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private RefundPersistenceAdapter realRefundAdapter;

    @Autowired
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Autowired
    private ProcessorPersistenceAdapter processorAdapter;

    @Autowired
    private SpringDataRefundRepository springDataRefundRepository;

    @Autowired
    private SpringDataPaymentRepository springDataPaymentRepository;

    @Autowired
    private SpringDataMerchantRepository springDataMerchantRepository;

    @Autowired
    private SpringDataProcessorCredentialRepository springDataProcessorCredentialRepository;

    @Autowired
    private SpringDataProcessorRepository springDataProcessorRepository;

    @BeforeEach
    void setUp() {
        springDataRefundRepository.deleteAll();
        springDataPaymentRepository.deleteAll();
        springDataProcessorCredentialRepository.deleteAll();
        springDataProcessorRepository.deleteAll();
        springDataMerchantRepository.deleteAll();
    }

    @Test
    @DisplayName("Optimistic locking: two concurrent state transitions on same refund version -> exactly one succeeds, one fails with RefundConcurrentModificationException, no lost update")
    void shouldPreventLostUpdateWhenTwoThreadsConcurrentlyUpdateSameRefund() throws Exception {
        // 1. Crear y persistir entidades requeridas
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Refund OptLock", "m_refund_optlock@test.com", Instant.now()));

        UUID processorId = UUID.randomUUID();
        processorAdapter.save(new Processor(processorId, "Processor Refund OptLock", Instant.now()));

        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                merchantId,
                100000L,
                Currency.COP,
                "pay-key-opt-refund",
                Instant.now()
        );
        payment.approve();
        paymentAdapter.save(payment);

        UUID refundId = UUID.randomUUID();
        Refund initialRefund = new Refund(
                refundId,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                "ref-opt-lock-key-1",
                Instant.now()
        );
        realRefundAdapter.save(initialRefund);

        // 2. Verificar que en base de datos tiene version = 0
        RefundEntity initialEntity = springDataRefundRepository.findById(refundId).orElseThrow();
        assertEquals(0L, initialEntity.getVersion());

        // 3. Decorador que sincroniza con barrera a los 2 hilos inmediatamente después de que ambos leen version = 0
        ConcurrentBarrierFindByIdDecorator barrierAdapter =
                new ConcurrentBarrierFindByIdDecorator(realRefundAdapter, 2);

        ApproveRefundUseCase approveUseCase = new ApproveRefundUseCase(barrierAdapter, processorAdapter);
        DeclineRefundUseCase declineUseCase = new DeclineRefundUseCase(barrierAdapter, processorAdapter);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Refund> approveFuture = executor.submit(() -> approveUseCase.execute(refundId, processorId));
            Future<Refund> declineFuture = executor.submit(() -> declineUseCase.execute(refundId, processorId));

            List<Future<Refund>> futures = List.of(approveFuture, declineFuture);
            int successCount = 0;
            int conflictCount = 0;
            Refund winnerRefund = null;

            for (Future<Refund> future : futures) {
                try {
                    Refund result = future.get(5, TimeUnit.SECONDS);
                    successCount++;
                    winnerRefund = result;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RefundConcurrentModificationException) {
                        conflictCount++;
                    } else {
                        throw e;
                    }
                }
            }

            // 4. Exactamente uno gana y exactamente uno falla por bloqueo optimista
            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertNotNull(winnerRefund);

            // 5. Verificación en PostgreSQL: version final = 1 y el estado final coincide exactamente con el ganador
            RefundEntity finalEntity = springDataRefundRepository.findById(refundId).orElseThrow();
            assertEquals(1L, finalEntity.getVersion());
            assertEquals(winnerRefund.getStatus(), finalEntity.getStatus());
            assertEquals(1L, springDataRefundRepository.count());
        } finally {
            executor.shutdown();
        }
    }

    private static class ConcurrentBarrierFindByIdDecorator implements RefundRepositoryPort {

        private final RefundRepositoryPort delegate;
        private final AtomicInteger readCounter = new AtomicInteger(0);
        private final CyclicBarrier barrier;

        ConcurrentBarrierFindByIdDecorator(RefundRepositoryPort delegate, int parties) {
            this.delegate = delegate;
            this.barrier = new CyclicBarrier(parties);
        }

        private void syncRead(Optional<Refund> result) {
            int call = readCounter.incrementAndGet();
            if (call <= 2 && result.isPresent()) {
                Refund refund = result.get();
                // Verificación explícita de que la lectura sincronizada obtuvo version = 0L
                assertEquals(0L, refund.getVersion(), "Synchronized read must see version 0L");

                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted waiting at barrier", e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException("Barrier timeout or failure during concurrent read", e);
                }
            }
        }

        @Override
        public Optional<Refund> findById(UUID id) {
            Optional<Refund> result = delegate.findById(id);
            syncRead(result);
            return result;
        }

        @Override
        public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
            Optional<Refund> result = delegate.findByIdAndMerchantId(id, merchantId);
            syncRead(result);
            return result;
        }

        @Override
        public void save(Refund refund) {
            delegate.save(refund);
        }

        @Override
        public Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return delegate.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        }

        @Override
        public PageResult<Refund> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
            return delegate.findByMerchantId(merchantId, pageQuery);
        }
    }
}
