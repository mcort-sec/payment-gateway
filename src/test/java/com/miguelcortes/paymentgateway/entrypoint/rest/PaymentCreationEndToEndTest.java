package com.miguelcortes.paymentgateway.entrypoint.rest;

import com.jayway.jsonpath.JsonPath;
import com.miguelcortes.paymentgateway.application.command.CreateApiCredentialCommand;
import com.miguelcortes.paymentgateway.application.command.CreateProcessorCommand;
import com.miguelcortes.paymentgateway.application.command.CreateProcessorCredentialCommand;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiCredential;
import com.miguelcortes.paymentgateway.application.dto.GeneratedProcessorCredential;
import com.miguelcortes.paymentgateway.application.usecase.CreateApiCredentialUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateProcessorCredentialUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateProcessorUseCase;
import com.miguelcortes.paymentgateway.domain.model.Currency;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.MerchantPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.ProcessorPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.entity.PaymentEntity;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataApiCredentialRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorCredentialRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
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

    @Autowired
    private SpringDataMerchantRepository merchantRepository;

    @Autowired
    private SpringDataApiCredentialRepository apiCredentialRepository;

    @Autowired
    private SpringDataProcessorRepository processorRepository;

    @Autowired
    private SpringDataProcessorCredentialRepository processorCredentialRepository;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    @Autowired
    private ProcessorPersistenceAdapter processorAdapter;

    @Autowired
    private CreateApiCredentialUseCase createApiCredentialUseCase;

    @Autowired
    private CreateProcessorUseCase createProcessorUseCase;

    @Autowired
    private CreateProcessorCredentialUseCase createProcessorCredentialUseCase;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        apiCredentialRepository.deleteAll();
        processorCredentialRepository.deleteAll();
        processorRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    private GeneratedApiCredential createMerchantWithApiKey(String name, String email) {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, name, email, Instant.now()));
        return createApiCredentialUseCase.execute(new CreateApiCredentialCommand(merchantId));
    }

    private GeneratedProcessorCredential createProcessorWithApiKey(String name) {
        Processor processor = createProcessorUseCase.execute(new CreateProcessorCommand(name));
        return createProcessorCredentialUseCase.execute(new CreateProcessorCredentialCommand(processor.getId()));
    }

    @Test
    @DisplayName("1. Full flow: POST /payments with valid Bearer API key persists new payment in PostgreSQL")
    void shouldCreateAndPersistPaymentSuccessfully() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant E2E 1", "e2e1@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();

        String idempotencyKey = "e2e-create-key-1";
        long amount = 75000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(amount, currency);

        MvcResult result = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.currency").value(currency.name()))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andReturn();

        UUID generatedId = UUID.fromString(
                JsonPath.read(result.getResponse().getContentAsString(), "$.id")
        );

        String locationHeader = result.getResponse().getHeader("Location");
        assertThat(locationHeader).endsWith("/payments/" + generatedId);

        assertThat(paymentRepository.count()).isEqualTo(1L);

        Optional<PaymentEntity> persistedEntity = paymentRepository.findById(generatedId);
        assertThat(persistedEntity).isPresent();

        PaymentEntity entity = persistedEntity.get();
        assertThat(entity.getId()).isEqualTo(generatedId);
        assertThat(entity.getMerchantId()).isEqualTo(merchantId);
        assertThat(entity.getAmount()).isEqualTo(amount);
        assertThat(entity.getCurrency()).isEqualTo(currency);
        assertThat(entity.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(entity.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("2. Idempotency replay: repeated identical POST /payments returns same payment without duplicate rows")
    void shouldHandleIdempotentReplayWithoutCreatingDuplicateRows() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant E2E 2", "e2e2@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();

        String idempotencyKey = "e2e-replay-key-1";
        long amount = 120000L;
        Currency currency = Currency.USD;

        String requestJson = """
                {
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(amount, currency);

        MvcResult firstResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String firstJson = firstResult.getResponse().getContentAsString();
        UUID firstId = UUID.fromString(JsonPath.read(firstJson, "$.id"));
        String firstCreatedAt = JsonPath.read(firstJson, "$.createdAt");

        MvcResult secondResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String secondJson = secondResult.getResponse().getContentAsString();
        UUID secondId = UUID.fromString(JsonPath.read(secondJson, "$.id"));
        String secondCreatedAt = JsonPath.read(secondJson, "$.createdAt");

        assertThat(secondId).isEqualTo(firstId);
        assertThat(secondCreatedAt).isEqualTo(firstCreatedAt);

        assertThat(paymentRepository.count()).isEqualTo(1L);
        Optional<PaymentEntity> persisted = paymentRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getId()).isEqualTo(firstId);
    }

    @Test
    @DisplayName("3. Idempotency conflict: POST /payments with same Idempotency-Key but different payload returns 409 Conflict")
    void shouldReturn409ConflictWhenPayloadDiffersForSameIdempotencyKey() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant E2E 3", "e2e3@merchant.com");

        String idempotencyKey = "e2e-conflict-key-1";

        String firstPayload = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        String conflictingPayload = """
                {
                    "amount": 90000,
                    "currency": "COP"
                }
                """;

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.count()).isEqualTo(1L);

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Idempotency key was already used with different payment parameters"))
                .andExpect(jsonPath("$.path").value("/payments"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));

        assertThat(paymentRepository.count()).isEqualTo(1L);
        PaymentEntity entity = paymentRepository.findAll().get(0);
        assertThat(entity.getAmount()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("4. Create and retrieve: POST /payments followed by GET /payments/{id} with owner's API key returns 200 OK")
    void shouldCreatePaymentAndRetrieveItByIdSuccessfully() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant E2E 4", "e2e4@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();

        String idempotencyKey = "e2e-get-key-1";
        long amount = 65000L;
        Currency currency = Currency.COP;

        String requestJson = """
                {
                    "amount": %d,
                    "currency": "%s"
                }
                """.formatted(amount, currency);

        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String createJson = createResult.getResponse().getContentAsString();
        UUID createdId = UUID.fromString(JsonPath.read(createJson, "$.id"));
        String createdAt = JsonPath.read(createJson, "$.createdAt");

        mockMvc.perform(get("/payments/{id}", createdId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.currency").value(currency.name()))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        Optional<PaymentEntity> entity = paymentRepository.findById(createdId);
        assertThat(entity).isPresent();
        assertThat(entity.get().getId()).isEqualTo(createdId);
        assertThat(entity.get().getMerchantId()).isEqualTo(merchantId);
        assertThat(entity.get().getAmount()).isEqualTo(amount);
    }

    @Test
    @DisplayName("5. Multi-tenant isolation: Foreign merchant cannot retrieve payment belonging to another merchant (404 Not Found)")
    void shouldPreventCrossTenantAccessOnGetPayment() throws Exception {
        GeneratedApiCredential credA = createMerchantWithApiKey("Merchant A", "merchA@test.com");
        GeneratedApiCredential credB = createMerchantWithApiKey("Merchant B", "merchB@test.com");

        String requestJson = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credA.plaintextApiKey())
                        .header("Idempotency-Key", "merchA-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credB.plaintextApiKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found with id: " + paymentId))
                .andExpect(jsonPath("$.path").value("/payments/" + paymentId));
    }

    @Test
    @DisplayName("6. Cancel payment: Foreign merchant cannot cancel payment (404), owner cancels successfully (200)")
    void shouldCancelPaymentSuccessfullyAndPreventForeignMerchantCancellation() throws Exception {
        GeneratedApiCredential credA = createMerchantWithApiKey("Merchant A Cancel", "merchAcancel@test.com");
        GeneratedApiCredential credB = createMerchantWithApiKey("Merchant B Cancel", "merchBcancel@test.com");

        String requestJson = """
                {
                    "amount": 85000,
                    "currency": "USD"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credA.plaintextApiKey())
                        .header("Idempotency-Key", "cancel-e2e-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credB.plaintextApiKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Payment not found with id: " + paymentId));

        assertThat(paymentRepository.findById(paymentId).get().getStatus()).isEqualTo(PaymentStatus.PENDING);

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credA.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(paymentRepository.findById(paymentId).get().getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + credA.plaintextApiKey()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("7. Role separation: Merchant cannot call /approve or /decline (403), Processor approves/declines successfully (200)")
    void shouldEnforceRoleSeparationForApproveAndDecline() throws Exception {
        GeneratedApiCredential merchantCred = createMerchantWithApiKey("Merchant Role", "merchRole@test.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Processor Primary");

        // 1. Merchant creates payment
        String requestJson = """
                {
                    "amount": 100000,
                    "currency": "COP"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey())
                        .header("Idempotency-Key", "role-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. Merchant attempts /approve -> 403 Forbidden
        mockMvc.perform(post("/payments/{id}/approve", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"));

        // 3. Merchant attempts /decline -> 403 Forbidden
        mockMvc.perform(post("/payments/{id}/decline", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"));

        // 4. Processor attempts to create/get/cancel payment -> 403 Forbidden
        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey())
                        .header("Idempotency-Key", "proc-pay-attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/payments/{id}/cancel", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 5. Processor approves payment -> 200 OK APPROVED
        mockMvc.perform(post("/payments/{id}/approve", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify in PostgreSQL
        assertThat(paymentRepository.findById(paymentId).get().getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("8. Processor decline and suspension flow")
    void shouldHandleProcessorDeclineAndSuspendedState() throws Exception {
        GeneratedApiCredential merchantCred = createMerchantWithApiKey("Merchant Proc 2", "merchProc2@test.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Processor Second");

        // 1. Create payment
        String requestJson = """
                {
                    "amount": 40000,
                    "currency": "COP"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey())
                        .header("Idempotency-Key", "proc-decline-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 2. Decline payment -> 200 OK DECLINED
        mockMvc.perform(post("/payments/{id}/decline", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(paymentRepository.findById(paymentId).get().getStatus()).isEqualTo(PaymentStatus.DECLINED);

        // 3. Create another payment to test suspended processor
        MvcResult createResult2 = mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey())
                        .header("Idempotency-Key", "proc-suspend-pay-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        UUID paymentId2 = UUID.fromString(JsonPath.read(createResult2.getResponse().getContentAsString(), "$.id"));

        // Suspend processor
        Processor processor = processorAdapter.findById(procCred.credential().getProcessorId()).get();
        processor.suspend();
        processorAdapter.save(processor);

        // Processor key is valid cryptographically, but processor is SUSPENDED -> 403 Forbidden
        mockMvc.perform(post("/payments/{id}/approve", paymentId2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Processor is suspended: " + procCred.credential().getProcessorId()));
    }

    @Test
    @DisplayName("9. Unknown JSON property: POST /payments containing unknown property 'merchantId' returns 400 Bad Request")
    void shouldReturn400BadRequestWhenUnknownPropertyMerchantIdIsPassed() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Unknown", "unknown@test.com");

        String requestJson = """
                {
                    "merchantId": "%s",
                    "amount": 50000,
                    "currency": "COP"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "unknown-prop-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request or invalid field format"))
                .andExpect(jsonPath("$.path").value("/payments"));

        assertThat(paymentRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("10. Authentication failures: Missing, malformed, non-existent, or revoked API key returns 401 Unauthorized")
    void shouldReturn401UnauthorizedWhenApiKeyIsMissingOrInvalid() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Revoke", "revoke@test.com");

        String payload = """
                {
                    "amount": 50000,
                    "currency": "COP"
                }
                """;

        // Missing Authorization header
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", "auth-fail-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));

        // Malformed Bearer token
        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-format")
                        .header("Idempotency-Key", "auth-fail-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));

        // Non-existent API key
        String nonExistentKey = "pg_test_nonexist1234_1234567890123456789012345678901234567890123";
        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonExistentKey)
                        .header("Idempotency-Key", "auth-fail-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));

        // Revoked API key
        var entity = apiCredentialRepository.findById(cred.credential().getId()).get();
        entity.setStatus(com.miguelcortes.paymentgateway.domain.model.CredentialStatus.REVOKED);
        entity.setRevokedAt(Instant.now());
        apiCredentialRepository.save(entity);

        mockMvc.perform(post("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "auth-fail-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"));
    }

    @Test
    @DisplayName("11. Processor authentication failure: Valid syntax and existing prefix but mismatched secret returns 401 Unauthorized")
    void shouldReturn401UnauthorizedWhenProcessorApiKeyHasSecretMismatch() throws Exception {
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Processor Hash Mismatch");
        String plaintext = procCred.plaintextApiKey();

        char originalLast = plaintext.charAt(plaintext.length() - 1);
        char replacement = originalLast == 'A' ? 'B' : 'A';
        String tamperedProcKey = plaintext.substring(0, plaintext.length() - 1) + replacement;

        UUID randomPaymentId = UUID.randomUUID();

        mockMvc.perform(post("/payments/{id}/approve", randomPaymentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedProcKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing API key"))
                .andExpect(jsonPath("$.path").value("/payments/" + randomPaymentId + "/approve"));
    }
}
