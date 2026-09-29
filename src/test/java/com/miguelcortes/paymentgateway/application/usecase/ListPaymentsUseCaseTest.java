package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListPaymentsUseCaseTest {

    private CapturingPaymentRepository repository;
    private ListPaymentsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new CapturingPaymentRepository();
        useCase = new ListPaymentsUseCase(repository);
    }

    @Test
    @DisplayName("Should forward query to repository and return page result when inputs are valid")
    void shouldForwardQueryToRepositoryAndReturnPageResult() {
        UUID merchantId = UUID.randomUUID();
        PageQuery query = new PageQuery(0, 20);

        Payment payment = new Payment(
                UUID.randomUUID(),
                merchantId,
                10000L,
                Currency.COP,
                "key-1",
                Instant.now()
        );
        PageResult<Payment> expectedResult = new PageResult<>(
                List.of(payment),
                0,
                20,
                1L,
                1
        );
        repository.stubbedResult = expectedResult;

        PageResult<Payment> result = useCase.execute(merchantId, query);

        assertSame(expectedResult, result);
        assertEquals(merchantId, repository.capturedMerchantId);
        assertSame(query, repository.capturedPageQuery);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when requester merchant id is null")
    void shouldThrowExceptionWhenRequesterMerchantIdIsNull() {
        PageQuery query = new PageQuery(0, 20);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(null, query)
        );

        assertEquals("Requester merchant ID must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when page query is null")
    void shouldThrowExceptionWhenPageQueryIsNull() {
        UUID merchantId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(merchantId, null)
        );

        assertEquals("Page query must not be null", ex.getMessage());
    }

    private static class CapturingPaymentRepository implements PaymentRepositoryPort {
        private UUID capturedMerchantId;
        private PageQuery capturedPageQuery;
        private PageResult<Payment> stubbedResult;

        @Override
        public void save(Payment payment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<Payment> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
            this.capturedMerchantId = merchantId;
            this.capturedPageQuery = pageQuery;
            return stubbedResult;
        }
    }
}
