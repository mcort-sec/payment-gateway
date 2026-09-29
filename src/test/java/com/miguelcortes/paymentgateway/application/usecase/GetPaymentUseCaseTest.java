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
    @DisplayName("Should return payment when it exists in repository")
    void shouldReturnPaymentWhenItExists() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                UUID.randomUUID(),
                50000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        repository.save(payment);

        Payment result = useCase.execute(paymentId);

        assertSame(payment, result);
        assertEquals(paymentId, result.getId());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment does not exist in repository")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();

        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> useCase.execute(nonExistentId)
        );

        assertEquals("Payment not found with id: " + nonExistentId, exception.getMessage());
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
