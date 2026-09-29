package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.miguelcortes.paymentgateway.application.command.CreateRefundCommand;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.exception.RefundAmountExceedsAvailableException;
import com.miguelcortes.paymentgateway.application.exception.RefundConcurrentModificationException;
import com.miguelcortes.paymentgateway.application.exception.RefundNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.usecase.ApproveRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclineRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetRefundUseCase;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidRefundStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Refund;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiAccessDeniedHandler;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyAuthenticationEntryPoint;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyAuthenticationToken;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyParser;
import com.miguelcortes.paymentgateway.infrastructure.security.MerchantPrincipal;
import com.miguelcortes.paymentgateway.infrastructure.security.ProcessorPrincipal;
import com.miguelcortes.paymentgateway.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RefundController.class)
@Import({
        SecurityConfig.class,
        ApiKeyParser.class,
        ApiKeyAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateRefundUseCase createRefundUseCase;

    @MockitoBean
    private GetRefundUseCase getRefundUseCase;

    @MockitoBean
    private ApproveRefundUseCase approveRefundUseCase;

    @MockitoBean
    private DeclineRefundUseCase declineRefundUseCase;

    @MockitoBean
    private ApiCredentialRepositoryPort apiCredentialRepository;

    @MockitoBean
    private ProcessorCredentialRepositoryPort processorCredentialRepository;

    @MockitoBean
    private ApiKeyHasherPort apiKeyHasher;

    private RequestPostProcessor authenticatedMerchant(UUID merchantId) {
        return authentication(ApiKeyAuthenticationToken.authenticatedMerchant(new MerchantPrincipal(merchantId)));
    }

    private RequestPostProcessor authenticatedProcessor(UUID processorId) {
        return authentication(ApiKeyAuthenticationToken.authenticatedProcessor(new ProcessorPrincipal(processorId)));
    }

    @Test
    @DisplayName("Should create refund successfully and return 201 with Location header and body")
    void shouldCreateRefundSuccessfully() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");
        String idempotencyKey = "refund-req-123";

        Refund mockRefund = Refund.reconstitute(
                refundId,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                RefundStatus.PENDING,
                idempotencyKey,
                createdAt,
                0L
        );

        when(createRefundUseCase.execute(any(CreateRefundCommand.class))).thenReturn(mockRefund);

        String requestJson = """
                {
                    "amount": 30000
                }
                """;

        mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/refunds/" + refundId)))
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-29T10:00:00Z"));

        verify(createRefundUseCase).execute(new CreateRefundCommand(
                paymentId,
                merchantId,
                30000L,
                idempotencyKey
        ));
    }

    @Test
    @DisplayName("Should return 200 when GET /refunds/{id} is called by owner merchant")
    void shouldGetRefundSuccessfully() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");

        Refund mockRefund = Refund.reconstitute(
                refundId,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                RefundStatus.PENDING,
                "key-1",
                createdAt,
                0L
        );

        when(getRefundUseCase.execute(refundId, merchantId)).thenReturn(mockRefund);

        mockMvc.perform(get("/refunds/{id}", refundId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-29T10:00:00Z"));

        verify(getRefundUseCase).execute(refundId, merchantId);
    }

    @Test
    @DisplayName("Should return 200 when POST /refunds/{id}/approve is called by processor")
    void shouldApproveRefundSuccessfully() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");

        Refund mockRefund = Refund.reconstitute(
                refundId,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                RefundStatus.APPROVED,
                "key-1",
                createdAt,
                1L
        );

        when(approveRefundUseCase.execute(refundId, processorId)).thenReturn(mockRefund);

        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .with(authenticatedProcessor(processorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(approveRefundUseCase).execute(refundId, processorId);
    }

    @Test
    @DisplayName("Should return 200 when POST /refunds/{id}/decline is called by processor")
    void shouldDeclineRefundSuccessfully() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-29T10:00:00Z");

        Refund mockRefund = Refund.reconstitute(
                refundId,
                paymentId,
                merchantId,
                30000L,
                Currency.COP,
                RefundStatus.DECLINED,
                "key-1",
                createdAt,
                1L
        );

        when(declineRefundUseCase.execute(refundId, processorId)).thenReturn(mockRefund);

        mockMvc.perform(post("/refunds/{id}/decline", refundId)
                        .with(authenticatedProcessor(processorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        verify(declineRefundUseCase).execute(refundId, processorId);
    }

    @Test
    @DisplayName("Should return 404 when refund is not found")
    void shouldReturn404WhenRefundNotFound() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        when(getRefundUseCase.execute(refundId, merchantId))
                .thenThrow(new RefundNotFoundException(refundId));

        mockMvc.perform(get("/refunds/{id}", refundId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Refund not found with id: " + refundId));
    }

    @Test
    @DisplayName("Should return 409 when invalid refund state")
    void shouldReturn409WhenInvalidRefundState() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();

        when(approveRefundUseCase.execute(refundId, processorId))
                .thenThrow(new InvalidRefundStateException("Cannot approve refund with status APPROVED"));

        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .with(authenticatedProcessor(processorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cannot approve refund with status APPROVED"));
    }

    @Test
    @DisplayName("Should return 409 when refund amount exceeds available capacity")
    void shouldReturn409WhenRefundAmountExceedsAvailable() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        when(createRefundUseCase.execute(any(CreateRefundCommand.class)))
                .thenThrow(new RefundAmountExceedsAvailableException("Requested refund amount 150000 exceeds available capacity 100000"));

        mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "key-over-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 150000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Requested refund amount 150000 exceeds available capacity 100000"));
    }

    @Test
    @DisplayName("Should return 409 when concurrent modification on refund")
    void shouldReturn409WhenConcurrentModification() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID processorId = UUID.randomUUID();

        when(approveRefundUseCase.execute(refundId, processorId))
                .thenThrow(new RefundConcurrentModificationException("Refund was modified concurrently"));

        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .with(authenticatedProcessor(processorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Refund was modified concurrently"));
    }

    @Test
    @DisplayName("Should return 400 when amount is <= 0")
    void shouldReturn400WhenAmountIsZeroOrNegative() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "key-invalid-amount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("amount: Amount must be greater than 0"));

        verifyNoInteractions(createRefundUseCase);
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{paymentId}/refunds", paymentId)
                        .with(authenticatedMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"));

        verifyNoInteractions(createRefundUseCase);
    }
}
