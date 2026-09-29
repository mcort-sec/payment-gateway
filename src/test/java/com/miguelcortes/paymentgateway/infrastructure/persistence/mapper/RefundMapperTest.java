package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.RefundEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefundMapperTest {

    private RefundMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RefundMapper();
    }

    @Test
    @DisplayName("Should map new Refund with null version to entity with null version")
    void shouldMapNewRefundWithNullVersionToEntityWithNullVersion() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        Refund refund = new Refund(
                id,
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                "ref-map-new",
                createdAt
        );

        RefundEntity entity = mapper.toEntity(refund);

        assertEquals(id, entity.getId());
        assertEquals(paymentId, entity.getPaymentId());
        assertEquals(merchantId, entity.getMerchantId());
        assertEquals(50000L, entity.getAmount());
        assertEquals(Currency.COP, entity.getCurrency());
        assertEquals(RefundStatus.PENDING, entity.getStatus());
        assertEquals("ref-map-new", entity.getIdempotencyKey());
        assertEquals(createdAt, entity.getCreatedAt());
        assertNull(entity.getVersion());
    }

    @Test
    @DisplayName("Should map reconstituted Refund with version to RefundEntity preserving all fields")
    void shouldMapReconstitutedRefundWithVersionToRefundEntityPreservingAllFields() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T11:00:00Z");

        Refund refund = Refund.reconstitute(
                id,
                paymentId,
                merchantId,
                15000L,
                Currency.USD,
                RefundStatus.APPROVED,
                "ref-map-1",
                createdAt,
                2L
        );

        RefundEntity entity = mapper.toEntity(refund);

        assertEquals(id, entity.getId());
        assertEquals(paymentId, entity.getPaymentId());
        assertEquals(merchantId, entity.getMerchantId());
        assertEquals(15000L, entity.getAmount());
        assertEquals(Currency.USD, entity.getCurrency());
        assertEquals(RefundStatus.APPROVED, entity.getStatus());
        assertEquals("ref-map-1", entity.getIdempotencyKey());
        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals(2L, entity.getVersion());
    }

    @Test
    @DisplayName("Should map RefundEntity with version to Refund preserving all fields")
    void shouldMapRefundEntityWithVersionToRefundPreservingAllFields() {
        UUID id = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T12:00:00Z");

        RefundEntity entity = new RefundEntity(
                id,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                RefundStatus.DECLINED,
                "ref-map-2",
                createdAt,
                3L
        );

        Refund refund = mapper.toDomain(entity);

        assertEquals(id, refund.getId());
        assertEquals(paymentId, refund.getPaymentId());
        assertEquals(merchantId, refund.getMerchantId());
        assertEquals(30000L, refund.getAmount());
        assertEquals(Currency.COP, refund.getCurrency());
        assertEquals(RefundStatus.DECLINED, refund.getStatus());
        assertEquals("ref-map-2", refund.getIdempotencyKey());
        assertEquals(createdAt, refund.getCreatedAt());
        assertEquals(3L, refund.getVersion());
    }

    @Test
    @DisplayName("Should return null when mapping null Refund to entity")
    void shouldReturnNullWhenMappingNullRefundToEntity() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    @DisplayName("Should return null when mapping null entity to domain")
    void shouldReturnNullWhenMappingNullEntityToDomain() {
        assertNull(mapper.toDomain(null));
    }
}
