package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.PaymentConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.usecase.ApprovePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CancelPaymentUseCase;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
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
class PaymentOptimisticLockingConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private PaymentPersistenceAdapter realAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Autowired
    private SpringDataPaymentRepository springDataPaymentRepository;

    @Autowired
    private SpringDataMerchantRepository springDataMerchantRepository;

    @BeforeEach
    void setUp() {
        springDataPaymentRepository.deleteAll();
        springDataMerchantRepository.deleteAll();
    }

    @Test
    @DisplayName("Optimistic locking: two concurrent state transitions on same version -> exactly one succeeds, one fails with PaymentConcurrentModificationException, no lost update")
    void shouldPreventLostUpdateWhenTwoThreadsConcurrentlyUpdateSamePayment() throws Exception {
        // 1. Crear y persistir pago PENDING inicial
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant OptLock", "m_optlock@test.com", Instant.now()));

        Payment initialPayment = new Payment(
                paymentId,
                merchantId,
                100000L,
                Currency.COP,
                "opt-lock-key-1",
                Instant.now()
        );
        realAdapter.save(initialPayment);

        // Verificar que en base de datos tiene version = 0
        PaymentEntity initialEntity = springDataPaymentRepository.findById(paymentId).orElseThrow();
        assertEquals(0L, initialEntity.getVersion());

        // 2. Decorador que sincroniza con barrera a los 2 hilos inmediatamente después de que ambos leen version = 0
        ConcurrentBarrierFindByIdDecorator barrierAdapter =
                new ConcurrentBarrierFindByIdDecorator(realAdapter, 2);

        ApprovePaymentUseCase approveUseCase = new ApprovePaymentUseCase(barrierAdapter);
        CancelPaymentUseCase cancelUseCase = new CancelPaymentUseCase(barrierAdapter);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Payment> approveFuture = executor.submit(() -> approveUseCase.execute(paymentId));
            Future<Payment> cancelFuture = executor.submit(() -> cancelUseCase.execute(paymentId));

            List<Future<Payment>> futures = List.of(approveFuture, cancelFuture);
            int successCount = 0;
            int conflictCount = 0;
            Payment winnerPayment = null;

            for (Future<Payment> future : futures) {
                try {
                    Payment payment = future.get(5, TimeUnit.SECONDS);
                    successCount++;
                    winnerPayment = payment;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof PaymentConcurrentModificationException) {
                        conflictCount++;
                    } else {
                        throw e;
                    }
                }
            }

            // 3. Exactamente uno gana y exactamente uno falla por bloqueo optimista
            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertNotNull(winnerPayment);

            // 4. Verificación en PostgreSQL: version final = 1 y el estado final coincide exactamente con el ganador
            PaymentEntity finalEntity = springDataPaymentRepository.findById(paymentId).orElseThrow();
            assertEquals(1L, finalEntity.getVersion());
            assertEquals(winnerPayment.getStatus(), finalEntity.getStatus());
        } finally {
            executor.shutdown();
        }
    }

    private static class ConcurrentBarrierFindByIdDecorator implements PaymentRepositoryPort {

        private final PaymentRepositoryPort delegate;
        private final AtomicInteger readCounter = new AtomicInteger(0);
        private final CyclicBarrier barrier;

        ConcurrentBarrierFindByIdDecorator(PaymentRepositoryPort delegate, int parties) {
            this.delegate = delegate;
            this.barrier = new CyclicBarrier(parties);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            Optional<Payment> result = delegate.findById(id);

            int call = readCounter.incrementAndGet();
            if (call <= 2 && result.isPresent()) {
                Payment payment = result.get();
                // Verificación explícita de que la lectura sincronizada obtuvo version = 0L
                assertEquals(0L, payment.getVersion(), "Synchronized read must see version 0L");

                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted waiting at barrier", e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException("Barrier timeout or failure during concurrent read", e);
                }
            }

            return result;
        }

        @Override
        public void save(Payment payment) {
            delegate.save(payment);
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return delegate.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        }

        @Override
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return delegate.findByIdAndMerchantId(id, merchantId);
        }
    }
}
