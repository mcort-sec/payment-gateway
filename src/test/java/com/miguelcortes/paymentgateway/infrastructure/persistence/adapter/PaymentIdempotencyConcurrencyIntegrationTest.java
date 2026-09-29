package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.infrastructure.generator.UuidGenerator;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
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

@SpringBootTest
@Testcontainers
class PaymentIdempotencyConcurrencyIntegrationTest {

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

    private final IdGenerator idGenerator = new UuidGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();

    @BeforeEach
    void setUp() {
        springDataPaymentRepository.deleteAll();
        springDataMerchantRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent requests with same payload: both threads receive identical Payment and only 1 row is created")
    void shouldHandleConcurrentRequestsWithSamePayloadAndPersistSinglePayment() throws Exception {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Conc 1", "m_conc1@test.com", Instant.now()));

        String idempotencyKey = "concurrent-same-payload-1";
        long amount = 100000L;
        Currency currency = Currency.COP;

        CreatePaymentCommand command1 = new CreatePaymentCommand(merchantId, amount, currency, idempotencyKey);
        CreatePaymentCommand command2 = new CreatePaymentCommand(merchantId, amount, currency, idempotencyKey);

        ConcurrentBarrierPaymentRepositoryDecorator barrierAdapter =
                new ConcurrentBarrierPaymentRepositoryDecorator(realAdapter, 2);

        CreatePaymentUseCase useCase = new CreatePaymentUseCase(barrierAdapter, merchantAdapter, idGenerator, timeProvider);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Payment> future1 = executor.submit(() -> useCase.execute(command1));
            Future<Payment> future2 = executor.submit(() -> useCase.execute(command2));

            Payment payment1 = future1.get(5, TimeUnit.SECONDS);
            Payment payment2 = future2.get(5, TimeUnit.SECONDS);

            assertThat(payment1).isNotNull();
            assertThat(payment2).isNotNull();
            assertThat(payment1.getId()).isEqualTo(payment2.getId());
            assertThat(payment1.getMerchantId()).isEqualTo(merchantId);
            assertThat(payment1.getAmount()).isEqualTo(amount);
            assertThat(payment1.getCurrency()).isEqualTo(currency);
            assertThat(payment1.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(payment1.getCreatedAt()).isEqualTo(payment2.getCreatedAt());

            assertThat(springDataPaymentRepository.count()).isEqualTo(1L);
            Optional<PaymentEntity> entity = springDataPaymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            assertThat(entity).isPresent();
            assertThat(entity.get().getId()).isEqualTo(payment1.getId());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Concurrent requests with conflicting payload: one thread succeeds, one fails with IdempotencyConflictException, and only 1 row is created")
    void shouldHandleConcurrentRequestsWithConflictingPayloadAndThrowConflictException() throws Exception {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Merchant Conc 2", "m_conc2@test.com", Instant.now()));

        String idempotencyKey = "concurrent-diff-payload-1";

        CreatePaymentCommand command1 = new CreatePaymentCommand(merchantId, 50000L, Currency.USD, idempotencyKey);
        CreatePaymentCommand command2 = new CreatePaymentCommand(merchantId, 99000L, Currency.USD, idempotencyKey);

        ConcurrentBarrierPaymentRepositoryDecorator barrierAdapter =
                new ConcurrentBarrierPaymentRepositoryDecorator(realAdapter, 2);

        CreatePaymentUseCase useCase = new CreatePaymentUseCase(barrierAdapter, merchantAdapter, idGenerator, timeProvider);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Payment> future1 = executor.submit(() -> useCase.execute(command1));
            Future<Payment> future2 = executor.submit(() -> useCase.execute(command2));

            List<Future<Payment>> futures = List.of(future1, future2);
            int successCount = 0;
            int conflictCount = 0;
            Payment successfulPayment = null;

            for (Future<Payment> future : futures) {
                try {
                    Payment payment = future.get(5, TimeUnit.SECONDS);
                    successCount++;
                    successfulPayment = payment;
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
            assertThat(successfulPayment).isNotNull();

            assertThat(springDataPaymentRepository.count()).isEqualTo(1L);
            Optional<PaymentEntity> entity = springDataPaymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
            assertThat(entity).isPresent();
            assertThat(entity.get().getId()).isEqualTo(successfulPayment.getId());
            assertThat(entity.get().getAmount()).isEqualTo(successfulPayment.getAmount());
        } finally {
            executor.shutdown();
        }
    }

    private static class ConcurrentBarrierPaymentRepositoryDecorator implements PaymentRepositoryPort {

        private final PaymentRepositoryPort delegate;
        private final AtomicInteger precheckCounter = new AtomicInteger(0);
        private final CyclicBarrier barrier;

        ConcurrentBarrierPaymentRepositoryDecorator(PaymentRepositoryPort delegate, int parties) {
            this.delegate = delegate;
            this.barrier = new CyclicBarrier(parties);
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            Optional<Payment> result = delegate.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);

            int call = precheckCounter.incrementAndGet();
            if (call <= 2 && result.isEmpty()) {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted waiting at barrier", e);
                } catch (BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException("Barrier wait failed or timed out", e);
                }
            }

            return result;
        }

        @Override
        public void save(Payment payment) {
            delegate.save(payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return delegate.findByIdAndMerchantId(id, merchantId);
        }
    }
}
