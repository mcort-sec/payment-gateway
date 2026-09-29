package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.jayway.jsonpath.JsonPath;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentCreationEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataPaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Full flow: POST /payments persists new payment in real PostgreSQL and returns 201 Created")
    void shouldCreateAndPersistPaymentSuccessfully() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-create-key-1";
        long amount = 75000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        MvcResult result = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.currency").value(currency.name()))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andReturn();

        UUID generatedId = UUID.fromString(
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.id"
                )
        );

        // Header Location verification
        String locationHeader = result.getResponse().getHeader("Location");
        assertThat(locationHeader).endsWith("/payments/" + generatedId);

        // Verification in PostgreSQL
        assertThat(paymentRepository.count()).isEqualTo(1L);

        Optional<PaymentEntity> persistedEntity = paymentRepository.findById(generatedId);
        assertThat(persistedEntity).isPresent();

        PaymentEntity entity = persistedEntity.get();
        assertThat(entity.getId()).isEqualTo(generatedId);
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getAmount()).isEqualTo(amount);
        assertThat(entity.getCurrency()).isEqualTo(currency);
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(entity.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. Idempotency replay: repeated identical POST /payments returns the same payment and creates no duplicate rows")
    void shouldHandleIdempotentReplayWithoutCreatingDuplicateRows() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-replay-key-1";
        long amount = 120000L;
        Currency currency = Currency.USD;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // First request
        MvcResult firstResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String firstJson = firstResult.getResponse().getContentAsString();
        UUID firstId = UUID.fromString(JsonPath.read(firstJson, "$.id"));
        String firstCreatedAt = JsonPath.read(firstJson, "$.createdAt");

        // Second identical request (Replay)
        MvcResult secondResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String secondJson = secondResult.getResponse().getContentAsString();
        UUID secondId = UUID.fromString(JsonPath.read(secondJson, "$.id"));
        String secondCreatedAt = JsonPath.read(secondJson, "$.createdAt");

        // Assert both responses point to the same payment
        assertThat(secondId).isEqualTo(firstId);
        assertThat(secondCreatedAt).isEqualTo(firstCreatedAt);

        // Assert database has exactly 1 row
        assertThat(paymentRepository.count()).isEqualTo(1L);
        Optional<PaymentEntity> persisted = paymentRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getId()).isEqualTo(firstId);
    }

    @Test
    @DisplayName("3. Idempotency conflict: POST /payments with same Idempotency-Key but different payload returns 409 Conflict")
    void shouldReturn409ConflictWhenPayloadDiffersForSameIdempotencyKey() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-conflict-key-1";

        String firstPayload = """
                {
                    "customerId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(customerId);

        String conflictingPayload = """
                {
                    "customerId": "%s",
                    "amount": 90000,
                    "currency": "COP"
                }
                """.formatted(customerId);

        // 1. First request succeeds
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.count()).isEqualTo(1L);

        // 2. Conflicting request fails with 409 Conflict
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Idempotency key was already used with different payment parameters"))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        // 3. Verify in PostgreSQL that no second row was inserted and original row remains intact
        assertThat(paymentRepository.count()).isEqualTo(1L);
        PaymentEntity entity = paymentRepository.findAll().get(0);
        assertThat(entity.getAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("4. Create and retrieve: POST /payments followed by GET /payments/{id} returns the same payment")
    void shouldCreatePaymentAndRetrieveItByIdSuccessfully() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-get-key-1";
        long amount = 65000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // 1. POST /payments
        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String createJson = createResult.getResponse().getContentAsString();
        UUID createdId = UUID.fromString(JsonPath.read(createJson, "$.id"));
        String createdAt = JsonPath.read(createJson, "$.createdAt");

        // 2. GET /payments/{id}
        mockMvc.perform(get("/payments/{id}", createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.currency").value(currency.name()))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        // 3. Verify in PostgreSQL
        Optional<PaymentEntity> entity = paymentRepository.findById(createdId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getId()).isEqualTo(createdId);
        assertThat(entity.get().getCustomerId()).isEqualTo(customerId);
        assertThat(entity.get().getAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("5. Approve payment flow: POST /payments -> POST /payments/{id}/approve -> GET /payments/{id} and subsequent approval conflict")
    void shouldApprovePaymentAndPreventSubsequentApprovalTransitions() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-approve-key-1";
        long amount = 150000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // 1. POST /payments -> Creates PENDING payment
        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. POST /payments/{id}/approve -> Returns APPROVED
        mockMvc.perform(post("/payments/{id}/approve", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. GET /payments/{id} -> Returns APPROVED
        mockMvc.perform(get("/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. Verify in PostgreSQL directly
        Optional<PaymentEntity> entity = paymentRepository.findById(paymentId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getStatus()).isEqualTo(PaymentStatus.APPROVED);

        // 5. Subsequent POST /payments/{id}/approve -> Returns 409 Conflict
        mockMvc.perform(post("/payments/{id}/approve", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot approve payment with status APPROVED"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/approve"));

        // 6. Verify in PostgreSQL that status remains APPROVED
        Optional<PaymentEntity> entityAfterConflict = paymentRepository.findById(paymentId);
        assertThat(entityAfterConflict).isPresent();
        assertThat(entityAfterConflict.get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("6. Decline payment flow: POST /payments -> POST /payments/{id}/decline -> GET /payments/{id} and subsequent decline conflict")
    void shouldDeclinePaymentAndPreventSubsequentDeclineTransitions() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-decline-key-1";
        long amount = 45000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // 1. POST /payments -> Creates PENDING payment
        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. POST /payments/{id}/decline -> Returns DECLINED
        mockMvc.perform(post("/payments/{id}/decline", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // 3. GET /payments/{id} -> Returns DECLINED
        mockMvc.perform(get("/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // 4. Verify in PostgreSQL directly
        Optional<PaymentEntity> entity = paymentRepository.findById(paymentId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getStatus()).isEqualTo(PaymentStatus.DECLINED);

        // 5. Subsequent POST /payments/{id}/decline -> Returns 409 Conflict
        mockMvc.perform(post("/payments/{id}/decline", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot decline payment with status DECLINED"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/decline"));

        // 6. Verify in PostgreSQL that status remains DECLINED
        Optional<PaymentEntity> entityAfterConflict = paymentRepository.findById(paymentId);
        assertThat(entityAfterConflict).isPresent();
        assertThat(entityAfterConflict.get().getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    @DisplayName("7. Cancel payment flow: POST /payments -> POST /payments/{id}/cancel -> GET /payments/{id} and subsequent cancel conflict")
    void shouldCancelPaymentAndPreventSubsequentCancelTransitions() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-cancel-key-1";
        long amount = 85000L;
        Currency currency = Currency.USD;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // 1. POST /payments -> Creates PENDING payment
        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. POST /payments/{id}/cancel -> Returns CANCELLED
        mockMvc.perform(post("/payments/{id}/cancel", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. GET /payments/{id} -> Returns CANCELLED
        mockMvc.perform(get("/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 4. Verify in PostgreSQL directly
        Optional<PaymentEntity> entity = paymentRepository.findById(paymentId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // 5. Subsequent POST /payments/{id}/cancel -> Returns 409 Conflict
        mockMvc.perform(post("/payments/{id}/cancel", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot cancel payment with status CANCELLED"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/cancel"));

        // 6. Verify in PostgreSQL that status remains CANCELLED
        Optional<PaymentEntity> entityAfterConflict = paymentRepository.findById(paymentId);
        assertThat(entityAfterConflict).isPresent();
        assertThat(entityAfterConflict.get().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("8. Cross-state conflict: Approved payment cannot be cancelled and remains APPROVED")
    void shouldPreventCancellingAlreadyApprovedPayment() throws Exception {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "e2e-cross-key-1";
        long amount = 110000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "customerId": "%s",
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(customerId, amount, currency);

        // 1. POST /payments -> Creates PENDING payment
        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. POST /payments/{id}/approve -> Sets APPROVED
        mockMvc.perform(post("/payments/{id}/approve", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. POST /payments/{id}/cancel -> Returns 409 Conflict
        mockMvc.perform(post("/payments/{id}/cancel", paymentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot cancel payment with status APPROVED"))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId + "/cancel"));

        // 4. Verify in PostgreSQL that status remains APPROVED
        Optional<PaymentEntity> entity = paymentRepository.findById(paymentId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }
}
