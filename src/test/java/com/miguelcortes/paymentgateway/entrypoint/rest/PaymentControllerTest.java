package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.miguelcortes.paymentgateway.application.command.CreatePaymentCommand;
import com.miguelcortes.paymentgateway.application.exception.IdempotencyConflictException;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.domain.exception.InvalidPaymentException;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePaymentUseCase createPaymentUseCase;

    @Test
    @DisplayName("Should create payment successfully and return 201 with Location header and body")
    void shouldCreatePaymentSuccessfully() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-28T20:00:00Z");
        String idempotencyKey = "req-test-123";

        Payment mockPayment = Payment.reconstitute(
                paymentId,
                customerId,
                50000L,
                Currency.COP,
                PaymentStatus.PENDING,
                idempotencyKey,
                createdAt
        );

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class))).thenReturn(mockPayment);

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(customerId);

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/payments/" + paymentId)))
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T20:00:00Z"));

        verify(createPaymentUseCase).execute(new CreatePaymentCommand(
                customerId,
                50000L,
                Currency.COP,
                idempotencyKey
        ));
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
        String tooLongKey = "a".repeat(65);
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 0,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
    @DisplayName("Should return 400 when customerId is null")
    void shouldReturn400WhenCustomerIdIsNull() throws Exception {
        String requestJson = """
                {
                    "customerId": null,
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "valid-key-2")
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
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": null
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "INVALID_CURRENCY"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
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
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "conflicting-key";

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new IdempotencyConflictException("Idempotency key was already used with different payment parameters"));

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(customerId);

        mockMvc.perform(post("/payments")
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
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "invalid-payment-key";

        when(createPaymentUseCase.execute(any(CreatePaymentCommand.class)))
                .thenThrow(new InvalidPaymentException("Payment ID cannot be null"));

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(customerId);

        mockMvc.perform(post("/payments")
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
}
