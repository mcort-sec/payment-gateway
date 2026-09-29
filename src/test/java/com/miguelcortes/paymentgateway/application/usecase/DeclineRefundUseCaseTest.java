package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.exception.RefundNotFoundException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
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

class DeclineRefundUseCaseTest {

    private InMemoryRefundRepository refundRepository;
    private InMemoryProcessorRepository processorRepository;
    private DeclineRefundUseCase useCase;

    private static final UUID ACTIVE_PROCESSOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_PROCESSOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        refundRepository = new InMemoryRefundRepository();
        processorRepository = new InMemoryProcessorRepository();
        useCase = new DeclineRefundUseCase(refundRepository, processorRepository);

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
    @DisplayName("Should decline PENDING refund and save it exactly once when processor is ACTIVE")
    void shouldDeclinePendingRefundAndSaveOnce() {
        UUID refundId = UUID.randomUUID();
        Refund pendingRefund = new Refund(
                refundId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                50000L,
                Currency.COP,
                "refund-key-1",
                Instant.now()
        );
        refundRepository.save(pendingRefund);
        refundRepository.resetCounters();

        Refund declinedRefund = useCase.execute(refundId, ACTIVE_PROCESSOR_ID);

        assertEquals(RefundStatus.DECLINED, declinedRefund.getStatus());
        assertSame(pendingRefund, declinedRefund);
        assertEquals(1, refundRepository.saveCallCount);
        assertEquals(1, refundRepository.findByIdCallCount);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when refundId or processorId is null")
    void shouldThrowIllegalArgumentExceptionWhenArgsAreNull() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, ACTIVE_PROCESSOR_ID));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("Should throw ProcessorNotFoundException and not query RefundRepositoryPort when processor does not exist")
    void shouldThrowProcessorNotFoundExceptionWhenProcessorDoesNotExist() {
        UUID nonExistentProcessorId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();

        ProcessorNotFoundException exception = assertThrows(
                ProcessorNotFoundException.class,
                () -> useCase.execute(refundId, nonExistentProcessorId)
        );

        assertEquals("Processor not found with id: " + nonExistentProcessorId, exception.getMessage());
        assertEquals(0, refundRepository.findByIdCallCount);
        assertEquals(0, refundRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw ProcessorSuspendedException and not query RefundRepositoryPort when processor is SUSPENDED")
    void shouldThrowProcessorSuspendedExceptionWhenProcessorIsSuspended() {
        UUID refundId = UUID.randomUUID();

        ProcessorSuspendedException exception = assertThrows(
                ProcessorSuspendedException.class,
                () -> useCase.execute(refundId, SUSPENDED_PROCESSOR_ID)
        );

        assertEquals("Processor is suspended: " + SUSPENDED_PROCESSOR_ID, exception.getMessage());
        assertEquals(0, refundRepository.findByIdCallCount);
        assertEquals(0, refundRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw RefundNotFoundException when refund does not exist")
    void shouldThrowRefundNotFoundExceptionWhenRefundDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        RefundNotFoundException exception = assertThrows(
                RefundNotFoundException.class,
                () -> useCase.execute(nonExistentId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals("Refund not found with id: " + nonExistentId, exception.getMessage());
        assertEquals(1, refundRepository.findByIdCallCount);
        assertEquals(0, refundRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidRefundStateException and not save when refund is already APPROVED")
    void shouldThrowInvalidRefundStateExceptionWhenAlreadyApproved() {
        UUID refundId = UUID.randomUUID();
        Refund refund = Refund.reconstitute(
                refundId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                50000L,
                Currency.COP,
                RefundStatus.APPROVED,
                "refund-key-2",
                Instant.now(),
                0L
        );
        refundRepository.save(refund);
        refundRepository.resetCounters();

        assertThrows(
                InvalidRefundStateException.class,
                () -> useCase.execute(refundId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals(1, refundRepository.findByIdCallCount);
        assertEquals(0, refundRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidRefundStateException and not save when refund is already DECLINED")
    void shouldThrowInvalidRefundStateExceptionWhenAlreadyDeclined() {
        UUID refundId = UUID.randomUUID();
        Refund refund = Refund.reconstitute(
                refundId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                50000L,
                Currency.COP,
                RefundStatus.DECLINED,
                "refund-key-3",
                Instant.now(),
                0L
        );
        refundRepository.save(refund);
        refundRepository.resetCounters();

        assertThrows(
                InvalidRefundStateException.class,
                () -> useCase.execute(refundId, ACTIVE_PROCESSOR_ID)
        );

        assertEquals(1, refundRepository.findByIdCallCount);
        assertEquals(0, refundRepository.saveCallCount);
    }

    private static class InMemoryRefundRepository implements RefundRepositoryPort {
        private final Map<UUID, Refund> storage = new HashMap<>();
        int saveCallCount = 0;
        int findByIdCallCount = 0;

        void resetCounters() {
            this.saveCallCount = 0;
            this.findByIdCallCount = 0;
        }

        @Override
        public void save(Refund refund) {
            saveCallCount++;
            storage.put(refund.getId(), refund);
        }

        @Override
        public Optional<Refund> findById(UUID id) {
            findByIdCallCount++;
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return Optional.ofNullable(storage.get(id))
                    .filter(r -> r.getMerchantId().equals(merchantId));
        }

        @Override
        public Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return storage.values().stream()
                    .filter(r -> r.getMerchantId().equals(merchantId) && r.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public PageResult<Refund> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
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
