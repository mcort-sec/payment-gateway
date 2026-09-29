package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreatePaymentUseCaseTest {

    private InMemoryPaymentRepository fakeRepository;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreatePaymentUseCase useCase;

    private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        fakeRepository = new InMemoryPaymentRepository();
        fakeIdGenerator = new FakeIdGenerator(FIXED_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);
        useCase = new CreatePaymentUseCase(fakeRepository, fakeIdGenerator, fakeTimeProvider);
    }

    @Test
    void shouldCreateAndSaveNewPaymentWhenItDoesNotExist() {
        UUID merchantId = UUID.randomUUID();
        CreatePaymentCommand command = new CreatePaymentCommand(
                merchantId,
                150000L,
                Currency.COP,
                "req-1001"
        );

        Payment createdPayment = useCase.execute(command);

        assertEquals(FIXED_ID, createdPayment.getId());
        assertEquals(merchantId, createdPayment.getMerchantId());
        assertEquals(150000L, createdPayment.getAmount());
        assertEquals(Currency.COP, createdPayment.getCurrency());
        assertEquals("req-1001", createdPayment.getIdempotencyKey());
        assertEquals(FIXED_TIME, createdPayment.getCreatedAt());
        assertEquals(PaymentStatus.PENDING, createdPayment.getStatus());

        assertEquals(1, fakeRepository.saveCallCount);
        assertSame(createdPayment, fakeRepository.lastSavedPayment);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    @Test
    void shouldReturnExistingPaymentWhenSamePayloadIsProvided() {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-1002";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                merchantId,
                200000L,
                Currency.COP,
                idempotencyKey,
                Instant.parse("2026-09-27T08:00:00Z")
        );
        fakeRepository.seed(existingPayment);

        CreatePaymentCommand command = new CreatePaymentCommand(
                merchantId,
                200000L,
                Currency.COP,
                idempotencyKey
        );

        Payment result = useCase.execute(command);

        assertSame(existingPayment, result);
        assertEquals(0, fakeRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    void shouldThrowExceptionWhenExistingPaymentHasDifferentAmount() {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-1003";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                merchantId,
                50000L,
                Currency.USD,
                idempotencyKey,
                Instant.now()
        );
        fakeRepository.seed(existingPayment);

        CreatePaymentCommand commandWithDifferentAmount = new CreatePaymentCommand(
                merchantId,
                99999L,
                Currency.USD,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentAmount)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(0, fakeRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    void shouldThrowExceptionWhenExistingPaymentHasDifferentCurrency() {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-1004";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                merchantId,
                1000L,
                Currency.USD,
                idempotencyKey,
                Instant.now()
        );
        fakeRepository.seed(existingPayment);

        CreatePaymentCommand commandWithDifferentCurrency = new CreatePaymentCommand(
                merchantId,
                1000L,
                Currency.COP,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentCurrency)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(0, fakeRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    void shouldRecoverAndReturnExistingPaymentWhenConcurrentInsertThrowsDuplicateKeyWithSamePayload() {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-1005";

        Payment concurrentWinner = new Payment(
                UUID.randomUUID(),
                merchantId,
                150000L,
                Currency.COP,
                idempotencyKey,
                FIXED_TIME
        );

        fakeRepository.simulateConcurrentDuplicateOnSave(concurrentWinner);

        CreatePaymentCommand command = new CreatePaymentCommand(
                merchantId,
                150000L,
                Currency.COP,
                idempotencyKey
        );

        Payment result = useCase.execute(command);

        assertSame(concurrentWinner, result);
        assertEquals(1, fakeRepository.saveCallCount);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    @Test
    void shouldThrowIdempotencyConflictWhenConcurrentInsertThrowsDuplicateKeyWithDifferentPayload() {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-1006";

        Payment concurrentWinner = new Payment(
                UUID.randomUUID(),
                merchantId,
                150000L,
                Currency.COP,
                idempotencyKey,
                FIXED_TIME
        );

        fakeRepository.simulateConcurrentDuplicateOnSave(concurrentWinner);

        CreatePaymentCommand commandWithDifferentAmount = new CreatePaymentCommand(
                merchantId,
                200000L,
                Currency.COP,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentAmount)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(1, fakeRepository.saveCallCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryPaymentRepository implements PaymentRepositoryPort {
        private final Map<String, Payment> storage = new HashMap<>();
        private int saveCallCount = 0;
        private Payment lastSavedPayment;
        private Payment concurrentWinnerToInjectOnSave;

        void seed(Payment payment) {
            storage.put(key(payment.getMerchantId(), payment.getIdempotencyKey()), payment);
        }

        void simulateConcurrentDuplicateOnSave(Payment winner) {
            this.concurrentWinnerToInjectOnSave = winner;
        }

        @Override
        public void save(Payment payment) {
            saveCallCount++;
            lastSavedPayment = payment;
            if (concurrentWinnerToInjectOnSave != null) {
                seed(concurrentWinnerToInjectOnSave);
                Payment winner = concurrentWinnerToInjectOnSave;
                concurrentWinnerToInjectOnSave = null;
                throw new DuplicateIdempotencyKeyException("Duplicate key violation for: " + winner.getIdempotencyKey());
            }
            storage.put(key(payment.getMerchantId(), payment.getIdempotencyKey()), payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return storage.values().stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return Optional.ofNullable(storage.get(key(merchantId, idempotencyKey)));
        }

        private String key(UUID merchantId, String idempotencyKey) {
            return merchantId + ":" + idempotencyKey;
        }
    }

    private static class FakeIdGenerator implements IdGenerator {
        private final UUID fixedId;
        private int callCount = 0;

        FakeIdGenerator(UUID fixedId) {
            this.fixedId = fixedId;
        }

        @Override
        public UUID generate() {
            callCount++;
            return fixedId;
        }
    }

    private static class FakeTimeProvider implements TimeProvider {
        private final Instant fixedTime;
        private int callCount = 0;

        FakeTimeProvider(Instant fixedTime) {
            this.fixedTime = fixedTime;
        }

        @Override
        public Instant now() {
            callCount++;
            return fixedTime;
        }
    }
}
