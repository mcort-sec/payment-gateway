package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListRefundsUseCaseTest {

    private CapturingRefundRepository repository;
    private ListRefundsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new CapturingRefundRepository();
        useCase = new ListRefundsUseCase(repository);
    }

    @Test
    @DisplayName("Should forward query to repository and return page result when inputs are valid")
    void shouldForwardQueryToRepositoryAndReturnPageResult() {
        UUID merchantId = UUID.randomUUID();
        PageQuery query = new PageQuery(0, 20);

        Refund refund = new Refund(
                UUID.randomUUID(),
                UUID.randomUUID(),
                merchantId,
                5000L,
                Currency.COP,
                "refund-key-1",
                Instant.now()
        );
        PageResult<Refund> expectedResult = new PageResult<>(
                List.of(refund),
                0,
                20,
                1L,
                1
        );
        repository.stubbedResult = expectedResult;

        PageResult<Refund> result = useCase.execute(merchantId, query);

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

    private static class CapturingRefundRepository implements RefundRepositoryPort {
        private UUID capturedMerchantId;
        private PageQuery capturedPageQuery;
        private PageResult<Refund> stubbedResult;

        @Override
        public void save(Refund refund) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Refund> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<Refund> findByMerchantId(UUID merchantId, PageQuery pageQuery) {
            this.capturedMerchantId = merchantId;
            this.capturedPageQuery = pageQuery;
            return stubbedResult;
        }
    }
}
