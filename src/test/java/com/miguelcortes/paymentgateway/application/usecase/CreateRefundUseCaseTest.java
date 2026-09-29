package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateRefundCommand;
import com.miguelcortes.paymentgateway.application.dto.RefundReservationResult;
import com.miguelcortes.paymentgateway.application.exception.DuplicateRefundIdempotencyKeyException;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.RefundAmountExceedsAvailableException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundReservationPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateRefundUseCaseTest {

    private InMemoryPaymentRepository fakePaymentRepository;
    private InMemoryRefundRepository fakeRefundRepository;
    private StubRefundReservationPort stubReservationPort;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreateRefundUseCase useCase;

    private static final UUID FIXED_REFUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-29T12:00:00Z");
    private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        fakePaymentRepository = new InMemoryPaymentRepository();
        fakeRefundRepository = new InMemoryRefundRepository();
        stubReservationPort = new StubRefundReservationPort();
        fakeIdGenerator = new FakeIdGenerator(FIXED_REFUND_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);

        useCase = new CreateRefundUseCase(
                fakePaymentRepository,
                fakeRefundRepository,
                stubReservationPort,
                fakeIdGenerator,
                fakeTimeProvider
        );

        Payment approvedPayment = Payment.reconstitute(
                PAYMENT_ID,
                MERCHANT_ID,
                100000L,
                Currency.COP,
                PaymentStatus.APPROVED,
                "pay-idemp-1",
                FIXED_TIME,
                1L
        );
        fakePaymentRepository.save(approvedPayment);
    }

    @Test
    @DisplayName("Should create PENDING refund on own APPROVED payment with capacity")
    void shouldCreatePendingRefundOnApprovedPayment() {
        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-idemp-1"
        );

        Refund refund = useCase.execute(command);

        assertNotNull(refund);
        assertEquals(FIXED_REFUND_ID, refund.getId());
        assertEquals(PAYMENT_ID, refund.getPaymentId());
        assertEquals(MERCHANT_ID, refund.getMerchantId());
        assertEquals(50000L, refund.getAmount());
        assertEquals(Currency.COP, refund.getCurrency());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals("ref-idemp-1", refund.getIdempotencyKey());
        assertEquals(FIXED_TIME, refund.getCreatedAt());
        assertNull(refund.getVersion());
    }

    @Test
    @DisplayName("Should copy currency from Payment to Refund")
    void shouldCopyCurrencyFromPayment() {
        UUID usdPaymentId = UUID.randomUUID();
        Payment usdPayment = Payment.reconstitute(
                usdPaymentId,
                MERCHANT_ID,
                2000L,
                Currency.USD,
                PaymentStatus.APPROVED,
                "pay-usd-1",
                FIXED_TIME,
                1L
        );
        fakePaymentRepository.save(usdPayment);

        CreateRefundCommand command = new CreateRefundCommand(
                usdPaymentId,
                MERCHANT_ID,
                1000L,
                "ref-usd-1"
        );

        Refund refund = useCase.execute(command);
        assertEquals(Currency.USD, refund.getCurrency());
    }

    @Test
    @DisplayName("Should allow partial refund")
    void shouldAllowPartialRefund() {
        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                25000L,
                "ref-partial-1"
        );

        Refund refund = useCase.execute(command);
        assertEquals(25000L, refund.getAmount());
    }

    @Test
    @DisplayName("Should allow full refund")
    void shouldAllowFullRefund() {
        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                100000L,
                "ref-full-1"
        );

        Refund refund = useCase.execute(command);
        assertEquals(100000L, refund.getAmount());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when Payment does not exist")
    void shouldThrowPaymentNotFoundWhenPaymentMissing() {
        UUID nonExistentPaymentId = UUID.randomUUID();
        CreateRefundCommand command = new CreateRefundCommand(
                nonExistentPaymentId,
                MERCHANT_ID,
                50000L,
                "ref-missing"
        );

        assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when Payment belongs to another merchant")
    void shouldThrowPaymentNotFoundWhenPaymentBelongsToOtherMerchant() {
        UUID otherMerchantId = UUID.randomUUID();
        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                otherMerchantId,
                50000L,
                "ref-foreign"
        );

        assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException when Payment is PENDING")
    void shouldThrowInvalidPaymentStateWhenPaymentIsPending() {
        UUID pendingPaymentId = UUID.randomUUID();
        Payment pendingPayment = Payment.reconstitute(
                pendingPaymentId,
                MERCHANT_ID,
                50000L,
                Currency.COP,
                PaymentStatus.PENDING,
                "pay-pending-1",
                FIXED_TIME,
                0L
        );
        fakePaymentRepository.save(pendingPayment);

        CreateRefundCommand command = new CreateRefundCommand(
                pendingPaymentId,
                MERCHANT_ID,
                20000L,
                "ref-pending"
        );

        InvalidPaymentStateException ex = assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(command)
        );
        assertEquals("Cannot create refund for payment with status PENDING", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException when Payment is DECLINED")
    void shouldThrowInvalidPaymentStateWhenPaymentIsDeclined() {
        UUID declinedPaymentId = UUID.randomUUID();
        Payment declinedPayment = Payment.reconstitute(
                declinedPaymentId,
                MERCHANT_ID,
                50000L,
                Currency.COP,
                PaymentStatus.DECLINED,
                "pay-declined-1",
                FIXED_TIME,
                1L
        );
        fakePaymentRepository.save(declinedPayment);

        CreateRefundCommand command = new CreateRefundCommand(
                declinedPaymentId,
                MERCHANT_ID,
                20000L,
                "ref-declined"
        );

        InvalidPaymentStateException ex = assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(command)
        );
        assertEquals("Cannot create refund for payment with status DECLINED", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException when Payment is CANCELLED")
    void shouldThrowInvalidPaymentStateWhenPaymentIsCancelled() {
        UUID cancelledPaymentId = UUID.randomUUID();
        Payment cancelledPayment = Payment.reconstitute(
                cancelledPaymentId,
                MERCHANT_ID,
                50000L,
                Currency.COP,
                PaymentStatus.CANCELLED,
                "pay-cancelled-1",
                FIXED_TIME,
                1L
        );
        fakePaymentRepository.save(cancelledPayment);

        CreateRefundCommand command = new CreateRefundCommand(
                cancelledPaymentId,
                MERCHANT_ID,
                20000L,
                "ref-cancelled"
        );

        InvalidPaymentStateException ex = assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(command)
        );
        assertEquals("Cannot create refund for payment with status CANCELLED", ex.getMessage());
    }

    @Test
    @DisplayName("Should return existing Refund on initial idempotency check when payload matches (replay)")
    void shouldReturnExistingRefundOnInitialCheckWhenPayloadMatches() {
        Refund existingRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                40000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-idemp-initial",
                FIXED_TIME,
                0L
        );
        fakeRefundRepository.save(existingRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                40000L,
                "ref-idemp-initial"
        );

        Refund result = useCase.execute(command);
        assertSame(existingRefund, result);
        assertEquals(0, stubReservationPort.reserveCallCount);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException on initial check when amount differs")
    void shouldThrowIdempotencyConflictOnInitialCheckWhenAmountDiffers() {
        Refund existingRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                40000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-idemp-conflict-1",
                FIXED_TIME,
                0L
        );
        fakeRefundRepository.save(existingRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                60000L,
                "ref-idemp-conflict-1"
        );

        assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException on initial check when paymentId differs")
    void shouldThrowIdempotencyConflictOnInitialCheckWhenPaymentIdDiffers() {
        UUID otherPaymentId = UUID.randomUUID();
        Refund existingRefund = Refund.reconstitute(
                UUID.randomUUID(),
                otherPaymentId,
                MERCHANT_ID,
                40000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-idemp-conflict-2",
                FIXED_TIME,
                0L
        );
        fakeRefundRepository.save(existingRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                40000L,
                "ref-idemp-conflict-2"
        );

        assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should return existing Refund when reservation port returns Existing with matching payload")
    void shouldReturnExistingWhenReservationReturnsExistingWithMatchingPayload() {
        Refund existingRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-res-existing-1",
                FIXED_TIME,
                0L
        );
        stubReservationPort.nextResult = new RefundReservationResult.Existing(existingRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-res-existing-1"
        );

        Refund result = useCase.execute(command);
        assertSame(existingRefund, result);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when reservation port returns Existing with different payload")
    void shouldThrowIdempotencyConflictWhenReservationReturnsExistingWithDifferentPayload() {
        Refund existingRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                30000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-res-existing-2",
                FIXED_TIME,
                0L
        );
        stubReservationPort.nextResult = new RefundReservationResult.Existing(existingRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-res-existing-2"
        );

        assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should throw RefundAmountExceedsAvailableException when reservation returns InsufficientCapacity")
    void shouldThrowRefundAmountExceedsAvailableExceptionWhenInsufficientCapacity() {
        stubReservationPort.nextResult = new RefundReservationResult.InsufficientCapacity(20000L);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-insufficient"
        );

        RefundAmountExceedsAvailableException ex = assertThrows(
                RefundAmountExceedsAvailableException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Requested refund amount 50000 exceeds available capacity 20000", ex.getMessage());
    }

    @Test
    @DisplayName("Should return winner when reserve() throws DuplicateRefundIdempotencyKeyException and winner matches payload")
    void shouldReturnWinnerOnDuplicateIdempotencyKeyWhenWinnerMatchesPayload() {
        Refund winnerRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-race-1",
                FIXED_TIME,
                0L
        );

        stubReservationPort.throwOnReserve = new DuplicateRefundIdempotencyKeyException("Duplicate key");
        fakeRefundRepository.save(winnerRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-race-1"
        );

        Refund result = useCase.execute(command);
        assertSame(winnerRefund, result);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException on DuplicateRefundIdempotencyKeyException when winner payload differs")
    void shouldThrowIdempotencyConflictOnDuplicateIdempotencyKeyWhenWinnerDiffers() {
        Refund winnerRefund = Refund.reconstitute(
                UUID.randomUUID(),
                PAYMENT_ID,
                MERCHANT_ID,
                30000L,
                Currency.COP,
                RefundStatus.PENDING,
                "ref-race-2",
                FIXED_TIME,
                0L
        );

        stubReservationPort.throwOnReserve = new DuplicateRefundIdempotencyKeyException("Duplicate key");
        fakeRefundRepository.save(winnerRefund);

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-race-2"
        );

        assertThrows(
                IdempotencyConflictException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should rethrow DuplicateRefundIdempotencyKeyException when winner is absent on lookup")
    void shouldRethrowDuplicateIdempotencyKeyWhenWinnerNotFound() {
        stubReservationPort.throwOnReserve = new DuplicateRefundIdempotencyKeyException("Duplicate key");

        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                50000L,
                "ref-race-absent"
        );

        assertThrows(
                DuplicateRefundIdempotencyKeyException.class,
                () -> useCase.execute(command)
        );
    }

    @Test
    @DisplayName("Should execute successfully without validating Merchant status")
    void shouldExecuteWithoutMerchantRepositoryValidation() {
        CreateRefundCommand command = new CreateRefundCommand(
                PAYMENT_ID,
                MERCHANT_ID,
                10000L,
                "ref-no-merchant-status"
        );

        Refund refund = useCase.execute(command);
        assertNotNull(refund);
        assertEquals(10000L, refund.getAmount());
    }

    // --- Fakes and Stubs ---

    private static class InMemoryPaymentRepository implements PaymentRepositoryPort {
        private final Map<UUID, Payment> storage = new HashMap<>();

        @Override
        public void save(Payment payment) {
            storage.put(payment.getId(), payment);
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            Payment p = storage.get(id);
            if (p != null && p.getMerchantId().equals(merchantId)) {
                return Optional.of(p);
            }
            return Optional.empty();
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

    private static class InMemoryRefundRepository implements RefundRepositoryPort {
        private final Map<UUID, Refund> storage = new HashMap<>();

        @Override
        public void save(Refund refund) {
            storage.put(refund.getId(), refund);
        }

        @Override
        public Optional<Refund> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
            Refund r = storage.get(id);
            if (r != null && r.getMerchantId().equals(merchantId)) {
                return Optional.of(r);
            }
            return Optional.empty();
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

    private static class StubRefundReservationPort implements RefundReservationPort {
        RefundReservationResult nextResult;
        RuntimeException throwOnReserve;
        int reserveCallCount = 0;

        @Override
        public RefundReservationResult reserve(Refund refund) {
            reserveCallCount++;
            if (throwOnReserve != null) {
                throw throwOnReserve;
            }
            if (nextResult != null) {
                return nextResult;
            }
            return new RefundReservationResult.Created(refund);
        }
    }

    private static class FakeIdGenerator implements IdGenerator {
        private final UUID fixedId;

        FakeIdGenerator(UUID fixedId) {
            this.fixedId = fixedId;
        }

        @Override
        public UUID generate() {
            return fixedId;
        }
    }

    private static class FakeTimeProvider implements TimeProvider {
        private final Instant fixedTime;

        FakeTimeProvider(Instant fixedTime) {
            this.fixedTime = fixedTime;
        }

        @Override
        public Instant now() {
            return fixedTime;
        }
    }
}
