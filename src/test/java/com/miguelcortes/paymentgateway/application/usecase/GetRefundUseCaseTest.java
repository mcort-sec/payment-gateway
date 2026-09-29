package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.exception.RefundNotFoundException;
import com.miguelcortes.paymentgateway.application.pagination.PageQuery;
import com.miguelcortes.paymentgateway.application.pagination.PageResult;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.Currency;
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

class GetRefundUseCaseTest {

    private InMemoryRefundRepository refundRepository;
    private GetRefundUseCase useCase;

    @BeforeEach
    void setUp() {
        refundRepository = new InMemoryRefundRepository();
        useCase = new GetRefundUseCase(refundRepository);
    }

    @Test
    @DisplayName("Should return refund when refund belongs to requesting merchant")
    void shouldReturnRefundWhenOwnedByMerchant() {
        UUID refundId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        Refund refund = new Refund(
                refundId,
                UUID.randomUUID(),
                merchantId,
                50000L,
                Currency.COP,
                "key-get-1",
                Instant.now()
        );
        refundRepository.save(refund);

        Refund result = useCase.execute(refundId, merchantId);

        assertSame(refund, result);
        assertEquals(refundId, result.getId());
        assertEquals(merchantId, result.getMerchantId());
    }

    @Test
    @DisplayName("Should throw RefundNotFoundException when refund does not exist")
    void shouldThrowRefundNotFoundExceptionWhenDoesNotExist() {
        UUID nonExistentRefundId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        RefundNotFoundException ex = assertThrows(
                RefundNotFoundException.class,
                () -> useCase.execute(nonExistentRefundId, merchantId)
        );

        assertEquals("Refund not found with id: " + nonExistentRefundId, ex.getMessage());
    }

    @Test
    @DisplayName("Should throw RefundNotFoundException when refund belongs to another merchant (tenant-safe 404)")
    void shouldThrowRefundNotFoundExceptionWhenBelongsToAnotherMerchant() {
        UUID refundId = UUID.randomUUID();
        UUID ownerMerchantId = UUID.randomUUID();
        UUID requesterMerchantId = UUID.randomUUID();

        Refund refund = new Refund(
                refundId,
                UUID.randomUUID(),
                ownerMerchantId,
                50000L,
                Currency.COP,
                "key-get-2",
                Instant.now()
        );
        refundRepository.save(refund);

        RefundNotFoundException ex = assertThrows(
                RefundNotFoundException.class,
                () -> useCase.execute(refundId, requesterMerchantId)
        );

        assertEquals("Refund not found with id: " + refundId, ex.getMessage());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when refundId or merchantId is null")
    void shouldThrowIllegalArgumentExceptionWhenArgsAreNull() {
        UUID validId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, validId));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(validId, null));
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
}
