package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.DuplicateIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.MerchantNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.MerchantSuspendedException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
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

class CreatePaymentUseCaseTest {

    private InMemoryPaymentRepository fakePaymentRepository;
    private InMemoryMerchantRepository fakeMerchantRepository;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreatePaymentUseCase useCase;

    private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");
    private static final UUID ACTIVE_MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        fakePaymentRepository = new InMemoryPaymentRepository();
        fakeMerchantRepository = new InMemoryMerchantRepository();
        fakeIdGenerator = new FakeIdGenerator(FIXED_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);

        useCase = new CreatePaymentUseCase(
                fakePaymentRepository,
                fakeMerchantRepository,
                fakeIdGenerator,
                fakeTimeProvider
        );

        Merchant activeMerchant = new Merchant(
                ACTIVE_MERCHANT_ID,
                "Acme Corp",
                "billing@acme.com",
                MerchantStatus.ACTIVE,
                FIXED_TIME
        );
        Merchant suspendedMerchant = new Merchant(
                SUSPENDED_MERCHANT_ID,
                "Suspended Corp",
                "suspended@acme.com",
                MerchantStatus.SUSPENDED,
                FIXED_TIME
        );

        fakeMerchantRepository.save(activeMerchant);
        fakeMerchantRepository.save(suspendedMerchant);
    }

    @Test
    @DisplayName("Should create and save new payment when merchant is ACTIVE and payment does not exist")
    void shouldCreateAndSaveNewPaymentWhenItDoesNotExist() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                150000L,
                Currency.COP,
                "req-1001"
        );

        Payment createdPayment = useCase.execute(command);

        assertEquals(FIXED_ID, createdPayment.getId());
        assertEquals(ACTIVE_MERCHANT_ID, createdPayment.getMerchantId());
        assertEquals(150000L, createdPayment.getAmount());
        assertEquals(Currency.COP, createdPayment.getCurrency());
        assertEquals("req-1001", createdPayment.getIdempotencyKey());
        assertEquals(FIXED_TIME, createdPayment.getCreatedAt());
        assertEquals(PaymentStatus.PENDING, createdPayment.getStatus());

        assertEquals(1, fakePaymentRepository.saveCallCount);
        assertSame(createdPayment, fakePaymentRepository.lastSavedPayment);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should return existing payment when same payload is provided for ACTIVE merchant")
    void shouldReturnExistingPaymentWhenSamePayloadIsProvided() {
        String idempotencyKey = "req-1002";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                ACTIVE_MERCHANT_ID,
                200000L,
                Currency.COP,
                idempotencyKey,
                Instant.parse("2026-09-27T08:00:00Z")
        );
        fakePaymentRepository.seed(existingPayment);

        CreatePaymentCommand command = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                200000L,
                Currency.COP,
                idempotencyKey
        );

        Payment result = useCase.execute(command);

        assertSame(existingPayment, result);
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when existing payment has different amount")
    void shouldThrowExceptionWhenExistingPaymentHasDifferentAmount() {
        String idempotencyKey = "req-1003";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                ACTIVE_MERCHANT_ID,
                50000L,
                Currency.USD,
                idempotencyKey,
                Instant.now()
        );
        fakePaymentRepository.seed(existingPayment);

        CreatePaymentCommand commandWithDifferentAmount = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                99999L,
                Currency.USD,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentAmount)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when existing payment has different currency")
    void shouldThrowExceptionWhenExistingPaymentHasDifferentCurrency() {
        String idempotencyKey = "req-1004";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                ACTIVE_MERCHANT_ID,
                1000L,
                Currency.USD,
                idempotencyKey,
                Instant.now()
        );
        fakePaymentRepository.seed(existingPayment);

        CreatePaymentCommand commandWithDifferentCurrency = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                1000L,
                Currency.COP,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentCurrency)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should recover and return existing payment when concurrent insert throws duplicate key with same payload")
    void shouldRecoverAndReturnExistingPaymentWhenConcurrentInsertThrowsDuplicateKeyWithSamePayload() {
        String idempotencyKey = "req-1005";

        Payment concurrentWinner = new Payment(
                UUID.randomUUID(),
                ACTIVE_MERCHANT_ID,
                150000L,
                Currency.COP,
                idempotencyKey,
                FIXED_TIME
        );

        fakePaymentRepository.simulateConcurrentDuplicateOnSave(concurrentWinner);

        CreatePaymentCommand command = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                150000L,
                Currency.COP,
                idempotencyKey
        );

        Payment result = useCase.execute(command);

        assertSame(concurrentWinner, result);
        assertEquals(1, fakePaymentRepository.saveCallCount);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when concurrent insert throws duplicate key with different payload")
    void shouldThrowIdempotencyConflictWhenConcurrentInsertThrowsDuplicateKeyWithDifferentPayload() {
        String idempotencyKey = "req-1006";

        Payment concurrentWinner = new Payment(
                UUID.randomUUID(),
                ACTIVE_MERCHANT_ID,
                150000L,
                Currency.COP,
                idempotencyKey,
                FIXED_TIME
        );

        fakePaymentRepository.simulateConcurrentDuplicateOnSave(concurrentWinner);

        CreatePaymentCommand commandWithDifferentAmount = new CreatePaymentCommand(
                ACTIVE_MERCHANT_ID,
                200000L,
                Currency.COP,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentAmount)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(1, fakePaymentRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw MerchantNotFoundException and not generate ID nor save when merchant does not exist")
    void shouldThrowMerchantNotFoundExceptionWhenMerchantDoesNotExist() {
        UUID nonExistentMerchantId = UUID.randomUUID();
        CreatePaymentCommand command = new CreatePaymentCommand(
                nonExistentMerchantId,
                150000L,
                Currency.COP,
                "req-2001"
        );

        MerchantNotFoundException exception = assertThrows(
                MerchantNotFoundException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Merchant not found with id: " + nonExistentMerchantId, exception.getMessage());
        assertEquals(nonExistentMerchantId, exception.getMerchantId());
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw MerchantSuspendedException and not generate ID nor save when merchant is SUSPENDED for new payment")
    void shouldThrowMerchantSuspendedExceptionWhenMerchantIsSuspendedForNewPayment() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                SUSPENDED_MERCHANT_ID,
                150000L,
                Currency.COP,
                "req-2002"
        );

        MerchantSuspendedException exception = assertThrows(
                MerchantSuspendedException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Merchant is suspended: " + SUSPENDED_MERCHANT_ID, exception.getMessage());
        assertEquals(SUSPENDED_MERCHANT_ID, exception.getMerchantId());
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should replay existing payment when merchant is SUSPENDED and payload matches")
    void shouldReplayExistingPaymentWhenMerchantIsSuspendedAndPayloadMatches() {
        String idempotencyKey = "req-2003";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                SUSPENDED_MERCHANT_ID,
                300000L,
                Currency.COP,
                idempotencyKey,
                Instant.parse("2026-09-27T12:00:00Z")
        );
        fakePaymentRepository.seed(existingPayment);

        CreatePaymentCommand command = new CreatePaymentCommand(
                SUSPENDED_MERCHANT_ID,
                300000L,
                Currency.COP,
                idempotencyKey
        );

        Payment result = useCase.execute(command);

        assertSame(existingPayment, result);
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when merchant is SUSPENDED and existing payload differs")
    void shouldThrowIdempotencyConflictWhenMerchantIsSuspendedAndExistingPayloadDiffers() {
        String idempotencyKey = "req-2004";

        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                SUSPENDED_MERCHANT_ID,
                300000L,
                Currency.COP,
                idempotencyKey,
                Instant.parse("2026-09-27T12:00:00Z")
        );
        fakePaymentRepository.seed(existingPayment);

        CreatePaymentCommand commandWithDifferentAmount = new CreatePaymentCommand(
                SUSPENDED_MERCHANT_ID,
                999999L,
                Currency.COP,
                idempotencyKey
        );

        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(commandWithDifferentAmount)
        );

        assertEquals("Idempotency key was already used with different payment parameters", exception.getMessage());
        assertEquals(0, fakePaymentRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryMerchantRepository implements MerchantRepositoryPort {
        private final Map<UUID, Merchant> storage = new HashMap<>();

        @Override
        public void save(Merchant merchant) {
            storage.put(merchant.getId(), merchant);
        }

        @Override
        public Optional<Merchant> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Merchant> findByEmail(String email) {
            return storage.values().stream()
                    .filter(m -> m.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        }
    }

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
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            return storage.values().stream()
                    .filter(p -> p.getId().equals(id) && p.getMerchantId().equals(merchantId))
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
