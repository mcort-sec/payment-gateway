package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.exception.MerchantNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.MerchantSuspendedException;
import com.miguelcortes.paymentgateway.application.exception.PaymentNotFoundException;
import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.usecase.ApprovePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CancelPaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclinePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetPaymentUseCase;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentStateException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiAccessDeniedHandler;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyAuthenticationEntryPoint;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyAuthenticationToken;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyParser;
import com.miguelcortes.paymentgateway.infrastructure.security.MerchantPrincipal;
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
import static org.hamcrest.Matchers.notNullValue;
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

@WebMvcTest(controllers = PaymentController.class)
@Import({
        SecurityConfig.class,
        ApiKeyParser.class,
        ApiKeyAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePaymentUseCase createPaymentUseCase;

    @MockitoBean
    private GetPaymentUseCase getPaymentUseCase;

    @MockitoBean
    private ApprovePaymentUseCase approvePaymentUseCase;

    @MockitoBean
    private DeclinePaymentUseCase declinePaymentUseCase;

    @MockitoBean
    private CancelPaymentUseCase cancelPaymentUseCase;

    @MockitoBean
    private ApiCredentialRepositoryPort apiCredentialRepository;

    @MockitoBean
    private ApiKeyHasherPort apiKeyHasher;

    private RequestPostProcessor authenticatedMerchant(UUID merchantId) {
        return authentication(ApiKeyAuthenticationToken.authenticated(new MerchantPrincipal(merchantId)));
    }

    @Test
    @DisplayName("Should create payment successfully and return 201 with Location header and body")
    void shouldCreatePaymentSuccessfully() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T20:00:00Z");
        String idempotencyKey = "req-test-123";

        Payment mockPayment = Payment.reconstitute(
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                PaymentStatus.PENDING,
                idempotencyKey,
                createdAt,
                0L
        );

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class))).thenReturn(mockPayment);

        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/payments/" + paymentId)))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T20:00:00Z"));

        verify(createPaymentUseCase).execute(new CreatePaymentCommand(
                merchantId,
                50000L,
                Currency.COP,
                idempotencyKey
        ));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when unknown property 'merchantId' is passed in request body")
    void shouldReturn400WhenUnknownPropertyMerchantIdIsPassed() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "req-unknown-prop";

        String requestJson = """
                {
                    "merchantId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field format"))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when POST /payments is called without authentication")
    void shouldReturn401WhenUnauthenticatedOnPostPayments() throws Exception {
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "key-unauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when GET /payments/{id} is called without authentication")
    void shouldReturn401WhenUnauthenticatedOnGetPayment() throws Exception {
        mockMvc.perform(get("/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));

        verifyNoInteractions(getPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when POST /payments/{id}/cancel is called without authentication")
    void shouldReturn401WhenUnauthenticatedOnCancelPayment() throws Exception {
        mockMvc.perform(post("/payments/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));

        verifyNoInteractions(cancelPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 403 Forbidden when authenticated merchant calls POST /payments/{id}/approve (denyAll)")
    void shouldReturn403WhenCallingApprovePayment() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{id}/approve", paymentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/approve"));

        verifyNoInteractions(approvePaymentUseCase);
    }

    @Test
    @DisplayName("Should return 403 Forbidden when authenticated merchant calls POST /payments/{id}/decline (denyAll)")
    void shouldReturn403WhenCallingDeclinePayment() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{id}/decline", paymentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/decline"));

        verifyNoInteractions(declinePaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header is blank")
    void shouldReturn400WhenIdempotencyKeyHeaderIsBlank() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header exceeds 64 characters")
    void shouldReturn400WhenIdempotencyKeyHeaderExceeds64Characters() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String tooLongKey = "a".repeat(65);
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", tooLongKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when amount is zero or negative")
    void shouldReturn400WhenAmountIsZeroOrNegative() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 0,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "valid-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when currency is null")
    void shouldReturn400WhenCurrencyIsNull() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": null
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "valid-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 400 when currency format is invalid in JSON")
    void shouldReturn400WhenCurrencyIsInvalid() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "INVALID_CURRENCY"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "valid-key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field format"))
                .andExpect(jsonPath("$.path").value("/payments"));

        verifyNoInteractions(createPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 409 Conflict when use case throws IdempotencyConflictException")
    void shouldReturn409WhenUseCaseThrowsIdempotencyConflictException() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "conflicting-key";

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new IdempotencyConflictException("Idempotency key was already used with different payment parameters"));

        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Idempotency key was already used with different payment parameters"))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when use case throws InvalidPaymentException")
    void shouldReturn400WhenUseCaseThrowsInvalidPaymentException() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String idempotencyKey = "invalid-payment-key";

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new InvalidPaymentException("Payment ID cannot be null"));

        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Payment ID cannot be null"))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    @DisplayName("Should return 200 OK and PaymentResponse when payment exists for GET /payments/{id}")
    void shouldReturn200WhenPaymentExists() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T21:00:00Z");

        Payment mockPayment = Payment.reconstitute(
                paymentId,
                merchantId,
                80000L,
                Currency.COP,
                PaymentStatus.PENDING,
                "key-get-1",
                createdAt,
                0L
        );

        when(getPaymentUseCase.execute(paymentId, merchantId)).thenReturn(mockPayment);

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(80000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T21:00:00Z"));

        verify(getPaymentUseCase).execute(paymentId, merchantId);
    }

    @Test
    @DisplayName("Should return 404 Not Found when payment does not exist for GET /payments/{id}")
    void shouldReturn404WhenPaymentDoesNotExist() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        when(getPaymentUseCase.execute(nonExistentId, merchantId))
                .thenThrow(new PaymentNotFoundException(nonExistentId));

        mockMvc.perform(get("/payments/{id}", nonExistentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found with id: " + nonExistentId))
                .andExpect(jsonPath("$.path").value("/payments/" + nonExistentId))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verify(getPaymentUseCase).execute(nonExistentId, merchantId);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when payment ID has invalid UUID format for GET /payments/{id}")
    void shouldReturn400WhenPaymentIdHasInvalidUuidFormat() throws Exception {
        UUID merchantId = UUID.randomUUID();

        mockMvc.perform(get("/payments/{id}", "not-a-valid-uuid")
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments/not-a-valid-uuid"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verifyNoInteractions(getPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 200 OK and CANCELLED status when cancelling payment successfully")
    void shouldReturn200WhenCancellingPaymentSuccessfully() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T21:00:00Z");

        Payment cancelledPayment = Payment.reconstitute(
                paymentId,
                merchantId,
                50000L,
                Currency.COP,
                PaymentStatus.CANCELLED,
                "key-cancel-1",
                createdAt,
                0L
        );

        when(cancelPaymentUseCase.execute(paymentId, merchantId)).thenReturn(cancelledPayment);

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T21:00:00Z"));

        verify(cancelPaymentUseCase).execute(paymentId, merchantId);
    }

    @Test
    @DisplayName("Should return 404 Not Found when cancelling non-existent payment")
    void shouldReturn404WhenCancellingNonExistentPayment() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        when(cancelPaymentUseCase.execute(nonExistentId, merchantId))
                .thenThrow(new PaymentNotFoundException(nonExistentId));

        mockMvc.perform(post("/payments/{id}/cancel", nonExistentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found with id: " + nonExistentId))
                .andExpect(jsonPath("$.path").value("/payments/" + nonExistentId + "/cancel"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verify(cancelPaymentUseCase).execute(nonExistentId, merchantId);
    }

    @Test
    @DisplayName("Should return 409 Conflict when cancelling payment with invalid state")
    void shouldReturn409WhenCancellingPaymentWithInvalidState() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        when(cancelPaymentUseCase.execute(paymentId, merchantId))
                .thenThrow(new InvalidPaymentStateException("Cannot cancel payment with status CANCELLED"));

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot cancel payment with status CANCELLED"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/cancel"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verify(cancelPaymentUseCase).execute(paymentId, merchantId);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when cancelling payment with invalid UUID format")
    void shouldReturn400WhenCancellingPaymentWithInvalidUuidFormat() throws Exception {
        UUID merchantId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{id}/cancel", "invalid-uuid")
                        .with(authenticatedMerchant(merchantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/payments/invalid-uuid/cancel"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        verifyNoInteractions(cancelPaymentUseCase);
    }

    @Test
    @DisplayName("Should return 404 Not Found when creating payment and merchant does not exist")
    void shouldReturn404WhenCreatingPaymentAndMerchantDoesNotExist() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new MerchantNotFoundException(merchantId));

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "valid-key-not-found")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Merchant not found with id: " + merchantId))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when creating payment and merchant is suspended")
    void shouldReturn403WhenCreatingPaymentAndMerchantIsSuspended() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new MerchantSuspendedException(merchantId));

        mockMvc.perform(post("/payments")
                        .with(authenticatedMerchant(merchantId))
                        .header("Idempotency-Key", "valid-key-suspended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Merchant is suspended: " + merchantId))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }
}
