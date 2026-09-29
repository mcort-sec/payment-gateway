package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
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

class GetPaymentUseCaseTest {

    private InMemoryPaymentRepository repository;
    private GetPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPaymentRepository();
        useCase = new GetPaymentUseCase(repository);
    }

    @Test
    @DisplayName("Should return payment when it exists and belongs to requester merchant")
    void shouldReturnPaymentWhenItExistsAndBelongsToMerchant() {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        repository.save(payment);

        Payment result = useCase.execute(paymentId, merchantId);

        assertSame(payment, result);
        assertEquals(paymentId, result.getId());
        assertEquals(merchantId, result.getMerchantId());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment does not exist in repository")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(nonExistentId, merchantId)
        );

        assertEquals("Payment not found with id: " + nonExistentId, exception.getMessage());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment belongs to a different merchant")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentBelongsToDifferentMerchant() {
        UUID paymentId = UUID.randomUUID();
        UUID ownerMerchantId = UUID.randomUUID();
        UUID foreignMerchantId = UUID.randomUUID();

        Payment payment = new Payment(
                paymentId,
                ownerMerchantId,
                50000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        repository.save(payment);

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(paymentId, foreignMerchantId)
        );

        assertEquals("Payment not found with id: " + paymentId, exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when payment id is null")
    void shouldThrowIllegalArgumentExceptionWhenIdIsNull() {
        UUID merchantId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(null, merchantId)
        );

        assertEquals("Payment ID must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when requester merchant id is null")
    void shouldThrowIllegalArgumentExceptionWhenRequesterMerchantIdIsNull() {
        UUID paymentId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(paymentId, null)
        );

        assertEquals("Requester merchant ID must not be null", exception.getMessage());
    }

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
            return Optional.ofNullable(storage.get(id))
                    .filter(p -> p.getMerchantId().equals(merchantId));
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return storage.values().stream()
                    .filter(p -> p.getMerchantId().equals(merchantId) && p.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }
    }
}
