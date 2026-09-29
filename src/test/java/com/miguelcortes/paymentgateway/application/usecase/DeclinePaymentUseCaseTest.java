package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeclinePaymentUseCaseTest {

    private InMemoryPaymentRepository paymentRepository;
    private InMemoryProcessorRepository processorRepository;
    private DeclinePaymentUseCase useCase;

    private static final UUID ACTIVE_PROCESSOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_PROCESSOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        processorRepository = new InMemoryProcessorRepository();
        useCase = new DeclinePaymentUseCase(paymentRepository, processorRepository);

        Processor activeProcessor = new Processor(
                ACTIVE_PROCESSOR_ID,
                "Active Processor",
                ProcessorStatus.ACTIVE,
                Instant.now()
        );
        Processor suspendedProcessor = new Processor(
                SUSPENDED_PROCESSOR_ID,
                "Suspended Processor",
                ProcessorStatus.SUSPENDED,
                Instant.now()
        );

        processorRepository.save(activeProcessor);
        processorRepository.save(suspendedProcessor);
    }

    @Test
    @DisplayName("Should decline PENDING payment and save it exactly once when processor is ACTIVE")
    void shouldDeclinePendingPaymentAndSaveOnce() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = new Payment(
                paymentId,
                UUID.randomUUID(),
                100000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        paymentRepository.save(pendingPayment);
        paymentRepository.resetSaveCounter();

        Payment declinedPayment = useCase.execute(paymentId, ACTIVE_PROCESSOR_ID);

        assertEquals(PaymentStatus.DECLINED, declinedPayment.getStatus());
        assertSame(pendingPayment, declinedPayment);
        assertEquals(1, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when paymentId or processorId is null")
    void shouldThrowIllegalArgumentExceptionWhenArgsAreNull() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, ACTIVE_PROCESSOR_ID));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("Should throw ProcessorNotFoundException when processor does not exist")
    void shouldThrowProcessorNotFoundExceptionWhenProcessorDoesNotExist() {
        UUID nonExistentProcessorId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        ProcessorNotFoundException exception = assertThrows(
                ProcessorNotFoundException.class,
                () -> useCase.execute(paymentId, nonExistentProcessorId)
        );

        assertEquals("Processor not found with id: " + nonExistentProcessorId, exception.getMessage());
        assertEquals(0, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw ProcessorSuspendedException when processor is SUSPENDED")
    void shouldThrowProcessorSuspendedExceptionWhenProcessorIsSuspended() {
        UUID paymentId = UUID.randomUUID();

        ProcessorSuspendedException exception = assertThrows(
                ProcessorSuspendedException.class,
                () -> useCase.execute(paymentId, SUSPENDED_PROCESSOR_ID)
        );

        assertEquals("Processor is suspended: " + SUSPENDED_PROCESSOR_ID, exception.getMessage());
        assertEquals(0, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment does not exist")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(nonExistentId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals("Payment not found with id: " + nonExistentId, exception.getMessage());
        assertEquals(0, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException and not save when payment is already APPROVED")
    void shouldThrowInvalidPaymentStateExceptionWhenAlreadyApproved() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.reconstitute(
                paymentId,
                UUID.randomUUID(),
                100000L,
                Currency.COP,
                PaymentStatus.APPROVED,
                "key-2",
                Instant.now(),
                0L
        );
        paymentRepository.save(payment);
        paymentRepository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals(0, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException and not save when payment is already DECLINED")
    void shouldThrowInvalidPaymentStateExceptionWhenAlreadyDeclined() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.reconstitute(
                paymentId,
                UUID.randomUUID(),
                100000L,
                Currency.COP,
                PaymentStatus.DECLINED,
                "key-3",
                Instant.now(),
                0L
        );
        paymentRepository.save(payment);
        paymentRepository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals(0, paymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException and not save when payment is already CANCELLED")
    void shouldThrowInvalidPaymentStateExceptionWhenAlreadyCancelled() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.reconstitute(
                paymentId,
                UUID.randomUUID(),
                100000L,
                Currency.COP,
                PaymentStatus.CANCELLED,
                "key-4",
                Instant.now(),
                0L
        );
        paymentRepository.save(payment);
        paymentRepository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals(0, paymentRepository.saveCallCount);
    }

    private static class InMemoryPaymentRepository implements PaymentRepositoryPort {
        private final Map<UUID, Payment> storage = new HashMap<>();
        int saveCallCount = 0;

        void resetSaveCounter() {
            this.saveCallCount = 0;
        }

        @Override
        public void save(Payment payment) {
            saveCallCount++;
            storage.put(payment.getId(), payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return Optional.ofNullable(storage.get(id))
                    .filter(p -> p.getMerchantId().equals(merchantId));
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return storage.values().stream()
                    .filter(p -> p.getMerchantId().equals(merchantId) && p.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public PageResult<Payment> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryProcessorRepository implements ProcessorRepositoryPort {
        private final Map<UUID, Processor> storage = new HashMap<>();

        @Override
        public void save(Processor processor) {
            storage.put(processor.getId(), processor);
        }

        @Override
        public Optional<Processor> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }
    }
}
