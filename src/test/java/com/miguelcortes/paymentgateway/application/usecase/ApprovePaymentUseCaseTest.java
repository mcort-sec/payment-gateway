package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
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

class ApprovePaymentUseCaseTest {

    private InMemoryPaymentRepository repository;
    private ApprovePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPaymentRepository();
        useCase = new ApprovePaymentUseCase(repository);
    }

    @Test
    @DisplayName("Should approve PENDING payment and save it exactly once")
    void shouldApprovePendingPaymentAndSaveOnce() {
        UUID paymentId = UUID.randomUUID();
        Payment pendingPayment = new Payment(
                paymentId,
                UUID.randomUUID(),
                100000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        repository.save(pendingPayment);
        repository.resetSaveCounter();

        Payment approvedPayment = useCase.execute(paymentId);

        assertEquals(PaymentStatus.APPROVED, approvedPayment.getStatus());
        assertSame(pendingPayment, approvedPayment);
        assertEquals(1, repository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment does not exist")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(nonExistentId)
        );

        assertEquals("Payment not found with id: " + nonExistentId, exception.getMessage());
        assertEquals(0, repository.saveCallCount);
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
        repository.save(payment);
        repository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId)
        );

        assertEquals(0, repository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException and not save when payment is DECLINED")
    void shouldThrowInvalidPaymentStateExceptionWhenDeclined() {
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
        repository.save(payment);
        repository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId)
        );

        assertEquals(0, repository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw InvalidPaymentStateException and not save when payment is CANCELLED")
    void shouldThrowInvalidPaymentStateExceptionWhenCancelled() {
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
        repository.save(payment);
        repository.resetSaveCounter();

        assertThrows(
                InvalidPaymentStateException.class,
                () -> useCase.execute(paymentId)
        );

        assertEquals(0, repository.saveCallCount);
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
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return storage.values().stream()
                    .filter(p -> p.getMerchantId().equals(merchantId) && p.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }
    }
}
