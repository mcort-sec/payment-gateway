package com.miguelcortes.paymentgateway.infrastructure.persistence.mapper;

import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentMapperTest {

    private PaymentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentMapper();
    }

    @Test
    void shouldMapPaymentToPaymentEntityPreservingAllFieldsAndApprovedStatus() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T14:30:00Z");

        Payment payment = Payment.reconstitute(
                id,
                customerId,
                350000L,
                Currency.COP,
                PaymentStatus.APPROVED,
                "req-map-1",
                createdAt
        );

        PaymentEntity entity = mapper.toEntity(payment);

        assertEquals(id, entity.getId());
        assertEquals(customerId, entity.getCustomerId());
        assertEquals(350000L, entity.getAmount());
        assertEquals(Currency.COP, entity.getCurrency());
        assertEquals(PaymentStatus.APPROVED, entity.getStatus());
        assertEquals("req-map-1", entity.getIdempotencyKey());
        assertEquals(createdAt, entity.getCreatedAt());
    }

    @Test
    void shouldMapPaymentEntityToPaymentPreservingAllFieldsAndApprovedStatus() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T15:00:00Z");

        PaymentEntity entity = new PaymentEntity(
                id,
                customerId,
                5000L,
                Currency.USD,
                PaymentStatus.APPROVED,
                "req-map-2",
                createdAt
        );

        Payment payment = mapper.toDomain(entity);

        assertEquals(id, payment.getId());
        assertEquals(customerId, payment.getCustomerId());
        assertEquals(5000L, payment.getAmount());
        assertEquals(Currency.USD, payment.getCurrency());
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals("req-map-2", payment.getIdempotencyKey());
        assertEquals(createdAt, payment.getCreatedAt());
    }

    @Test
    void shouldPerformRoundTripMappingWithoutLossOfInformationForDeclinedStatus() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T16:00:00Z");

        Payment originalPayment = Payment.reconstitute(
                id,
                customerId,
                120000L,
                Currency.COP,
                PaymentStatus.DECLINED,
                "req-map-3",
                createdAt
        );

        PaymentEntity intermediateEntity = mapper.toEntity(originalPayment);
        Payment roundTripPayment = mapper.toDomain(intermediateEntity);

        assertEquals(originalPayment.getId(), roundTripPayment.getId());
        assertEquals(originalPayment.getCustomerId(), roundTripPayment.getCustomerId());
        assertEquals(originalPayment.getAmount(), roundTripPayment.getAmount());
        assertEquals(originalPayment.getCurrency(), roundTripPayment.getCurrency());
        assertEquals(originalPayment.getStatus(), roundTripPayment.getStatus());
        assertEquals(originalPayment.getIdempotencyKey(), roundTripPayment.getIdempotencyKey());
        assertEquals(originalPayment.getCreatedAt(), roundTripPayment.getCreatedAt());
    }

    @Test
    void shouldReturnNullWhenMappingNullPaymentToEntity() {
        assertNull(mapper.toEntity(null));
    }

    @Test
    void shouldReturnNullWhenMappingNullEntityToDomain() {
        assertNull(mapper.toDomain(null));
    }
}
