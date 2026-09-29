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
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.RefundStatus;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.MerchantPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.PaymentPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.ProcessorPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.adapter.RefundPersistenceAdapter;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataApiCredentialRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataMerchantRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataPaymentRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorCredentialRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataProcessorRepository;
import com.miguelcortes.paymentgateway.infrastructure.persistence.repository.SpringDataRefundRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RefundEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataRefundRepository refundRepository;

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
    private PaymentPersistenceAdapter paymentAdapter;

    @Autowired
    private RefundPersistenceAdapter refundAdapter;

    @Autowired
    private CreateApiCredentialUseCase createApiCredentialUseCase;

    @Autowired
    private CreateProcessorUseCase createProcessorUseCase;

    @Autowired
    private CreateProcessorCredentialUseCase createProcessorCredentialUseCase;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
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

    private Payment createApprovedPayment(UUID merchantId, long amount) {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(
                paymentId,
                merchantId,
                amount,
                Currency.COP,
                "pay-key-" + UUID.randomUUID(),
                Instant.now()
        );
        payment.approve();
        paymentAdapter.save(payment);
        return payment;
    }

    // ==========================================
    // 10. E2E — CREATE REFUND
    // ==========================================

    @Test
    @DisplayName("Create Refund: Active merchant + approved payment + partial amount -> 201 Created with Location and body")
    void shouldCreatePartialRefundSuccessfully() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Ref 1", "ref1@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        String idempotencyKey = "e2e-ref-key-1";
        long refundAmount = 40000L;

        MvcResult result = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + refundAmount + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(refundAmount))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value(RefundStatus.PENDING.name()))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.processorId").doesNotExist())
                .andReturn();

        UUID generatedId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        assertThat(result.getResponse().getHeader("Location")).endsWith("/refunds/" + generatedId);
        assertThat(refundRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Create Refund: Full refund -> 201 Created")
    void shouldCreateFullRefundSuccessfully() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Ref Full", "full@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "full-refund-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100000))
                .andExpect(jsonPath("$.status").value(RefundStatus.PENDING.name()));
    }

    @Test
    @DisplayName("Create Refund: Suspended merchant + approved payment -> 201 Created (refunds allowed)")
    void shouldAllowSuspendedMerchantToCreateRefund() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Suspended Ref", "susp_ref@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        Merchant merchant = merchantAdapter.findById(merchantId).get();
        merchant.suspend();
        merchantAdapter.save(merchant);

        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "susp-merchant-ref-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    @DisplayName("Create Refund: Idempotent replay with same key, payment, and amount -> 201 Created with same Refund ID")
    void shouldReplayIdenticalRefundRequestIdempotently() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Replay", "replay@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        String idempotencyKey = "replay-refund-key-1";

        MvcResult firstResult = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30000}"))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.id");

        MvcResult replayResult = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30000}"))
                .andExpect(status().isCreated())
                .andReturn();

        String replayId = JsonPath.read(replayResult.getResponse().getContentAsString(), "$.id");

        assertThat(replayId).isEqualTo(firstId);
        assertThat(refundRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Create Refund: Second partial within remaining capacity -> 201 Created")
    void shouldAllowSecondPartialRefundWithinCapacity() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Multi", "multi@merchant.com");
        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "partial-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 40000}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "partial-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 60000}"))
                .andExpect(status().isCreated());

        assertThat(refundRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Create Refund Errors: Auth, invalid inputs, payment states, and capacity")
    void shouldHandleCreateRefundErrors() throws Exception {
        GeneratedApiCredential cred1 = createMerchantWithApiKey("Merchant Err 1", "err1@merchant.com");
        GeneratedApiCredential cred2 = createMerchantWithApiKey("Merchant Err 2", "err2@merchant.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Proc E2E");

        UUID merchant1Id = cred1.credential().getMerchantId();
        Payment approvedPayment = createApprovedPayment(merchant1Id, 100000L);

        // 1. Anonymous -> 401
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header("Idempotency-Key", "anon-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isUnauthorized());

        // 2. Invalid merchant key -> 401
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .header("Idempotency-Key", "bad-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isUnauthorized());

        // 3. Processor key -> 403
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey())
                        .header("Idempotency-Key", "proc-role-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isForbidden());

        // 4. Foreign Payment -> 404
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred2.plaintextApiKey())
                        .header("Idempotency-Key", "foreign-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isNotFound());

        // 5. Nonexistent Payment -> 404
        mockMvc.perform(post("/payments/{paymentId}/refunds", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "nonexistent-pay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isNotFound());

        // 6. Payment PENDING -> 409
        UUID pendingPayId = UUID.randomUUID();
        Payment pendingPay = new Payment(pendingPayId, merchant1Id, 50000L, Currency.COP, "pending-key-test", Instant.now());
        paymentAdapter.save(pendingPay);

        mockMvc.perform(post("/payments/{paymentId}/refunds", pendingPayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "pend-pay-ref-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isConflict());

        // 7. Payment DECLINED -> 409
        UUID declinedPayId = UUID.randomUUID();
        Payment declinedPay = new Payment(declinedPayId, merchant1Id, 50000L, Currency.COP, "decl-key-test", Instant.now());
        declinedPay.decline();
        paymentAdapter.save(declinedPay);

        mockMvc.perform(post("/payments/{paymentId}/refunds", declinedPayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "decl-pay-ref-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isConflict());

        // 8. Payment CANCELLED -> 409
        UUID cancelledPayId = UUID.randomUUID();
        Payment cancelledPay = new Payment(cancelledPayId, merchant1Id, 50000L, Currency.COP, "canc-key-test", Instant.now());
        cancelledPay.cancel();
        paymentAdapter.save(cancelledPay);

        mockMvc.perform(post("/payments/{paymentId}/refunds", cancelledPayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "canc-pay-ref-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isConflict());

        // 9. Amount <= 0 -> 400
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "zero-amount-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());

        // 10. Missing Idempotency-Key -> 400
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isBadRequest());

        // 11. Blank Idempotency-Key -> 400
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isBadRequest());

        // 12. Idempotency-Key > 64 -> 400
        String longKey = "a".repeat(65);
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10000}"))
                .andExpect(status().isBadRequest());

        // 13. Same key / different amount -> 409
        String conflictKey = "conflict-key-1";
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 20000}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25000}"))
                .andExpect(status().isConflict());

        // 14. Same key / different Payment -> 409
        Payment secondApproved = createApprovedPayment(merchant1Id, 100000L);
        mockMvc.perform(post("/payments/{paymentId}/refunds", secondApproved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 20000}"))
                .andExpect(status().isConflict());

        // 15. Over-refund / insufficient available amount -> 409
        mockMvc.perform(post("/payments/{paymentId}/refunds", approvedPayment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "over-refund-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 90000}"))
                .andExpect(status().isConflict());
    }

    // ==========================================
    // 11. E2E — GET REFUND
    // ==========================================

    @Test
    @DisplayName("Get Refund: Owner merchant, foreign merchant, processor, anonymous")
    void shouldHandleGetRefundFlows() throws Exception {
        GeneratedApiCredential cred1 = createMerchantWithApiKey("Merchant Owner", "owner@merchant.com");
        GeneratedApiCredential cred2 = createMerchantWithApiKey("Merchant Foreign", "foreign@merchant.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Proc Get");

        UUID merchantId = cred1.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        MvcResult createResult = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey())
                        .header("Idempotency-Key", "get-ref-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 35000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 1. Owner Merchant -> 200 OK
        mockMvc.perform(get("/refunds/{id}", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.amount").value(35000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());

        // 2. Foreign Merchant -> 404 Not Found (tenant-safe)
        mockMvc.perform(get("/refunds/{id}", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred2.plaintextApiKey()))
                .andExpect(status().isNotFound());

        // 3. Nonexistent -> 404
        mockMvc.perform(get("/refunds/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred1.plaintextApiKey()))
                .andExpect(status().isNotFound());

        // 4. Processor key -> 403 Forbidden
        mockMvc.perform(get("/refunds/{id}", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // 5. Anonymous -> 401 Unauthorized
        mockMvc.perform(get("/refunds/{id}", refundId))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // 12. E2E — APPROVE REFUND
    // ==========================================

    @Test
    @DisplayName("Approve Refund: Active processor, suspended processor, merchant key, invalid transitions")
    void shouldHandleApproveRefundFlows() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Approve", "appr@merchant.com");
        GeneratedProcessorCredential procCredA = createProcessorWithApiKey("Proc A");
        GeneratedProcessorCredential procCredB = createProcessorWithApiKey("Proc B");

        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        MvcResult createResult = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "appr-ref-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // 1. Processor B approves refund created under Merchant A -> 200 APPROVED
        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredB.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.status").value(RefundStatus.APPROVED.name()));

        // 2. Already APPROVED -> 409 Conflict
        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isConflict());

        // 3. Already APPROVED -> decline also fails with 409 Conflict
        mockMvc.perform(post("/refunds/{id}/decline", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isConflict());

        // 4. Suspended processor -> 403 Forbidden
        Processor procA = processorAdapter.findById(procCredA.credential().getProcessorId()).get();
        procA.suspend();
        processorAdapter.save(procA);

        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // 5. Merchant key -> 403 Forbidden
        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // 6. Anonymous -> 401 Unauthorized
        mockMvc.perform(post("/refunds/{id}/approve", refundId))
                .andExpect(status().isUnauthorized());

        // 7. Nonexistent Refund -> 404 Not Found
        mockMvc.perform(post("/refunds/{id}/approve", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredB.plaintextApiKey()))
                .andExpect(status().isNotFound());

        // 8. DECLINED Refund -> approve fails with 409 Conflict
        MvcResult createResult2 = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "appr-ref-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 20000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refund2Id = UUID.fromString(JsonPath.read(createResult2.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/refunds/{id}/decline", refund2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredB.plaintextApiKey()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/refunds/{id}/approve", refund2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredB.plaintextApiKey()))
                .andExpect(status().isConflict());
    }

    // ==========================================
    // 13. E2E — DECLINE REFUND FLOWS
    // ==========================================

    @Test
    @DisplayName("Decline Refund: Active processor, suspended processor, anonymous, nonexistent, invalid state transitions")
    void shouldHandleDeclineRefundFlows() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Decline Flow", "decl_flow@merchant.com");
        GeneratedProcessorCredential procCredA = createProcessorWithApiKey("Proc Decl A");
        GeneratedProcessorCredential procCredB = createProcessorWithApiKey("Proc Decl B");

        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        MvcResult createResult1 = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-flow-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refund1Id = UUID.fromString(JsonPath.read(createResult1.getResponse().getContentAsString(), "$.id"));

        // 1. Active processor declines PENDING refund -> 200 DECLINED
        mockMvc.perform(post("/refunds/{id}/decline", refund1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refund1Id.toString()))
                .andExpect(jsonPath("$.status").value(RefundStatus.DECLINED.name()));

        // 2. Already DECLINED -> decline again fails with 409 Conflict
        mockMvc.perform(post("/refunds/{id}/decline", refund1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isConflict());

        // 3. Already DECLINED -> approve fails with 409 Conflict
        mockMvc.perform(post("/refunds/{id}/approve", refund1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isConflict());

        // 4. Already APPROVED -> decline fails with 409 Conflict
        MvcResult createResult2 = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-flow-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refund2Id = UUID.fromString(JsonPath.read(createResult2.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/refunds/{id}/approve", refund2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/refunds/{id}/decline", refund2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isConflict());

        // 5. Suspended processor -> 403 Forbidden
        Processor procB = processorAdapter.findById(procCredB.credential().getProcessorId()).get();
        procB.suspend();
        processorAdapter.save(procB);

        MvcResult createResult3 = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-flow-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refund3Id = UUID.fromString(JsonPath.read(createResult3.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/refunds/{id}/decline", refund3Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredB.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // 6. Merchant key -> 403 Forbidden
        mockMvc.perform(post("/refunds/{id}/decline", refund3Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // 7. Anonymous -> 401 Unauthorized
        mockMvc.perform(post("/refunds/{id}/decline", refund3Id))
                .andExpect(status().isUnauthorized());

        // 8. Nonexistent Refund -> 404 Not Found
        mockMvc.perform(post("/refunds/{id}/decline", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCredA.plaintextApiKey()))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // 14. E2E — DECLINE & CAPACITY RELEASE
    // ==========================================

    @Test
    @DisplayName("Decline Refund: Decline frees capacity allowing subsequent full refund")
    void shouldHandleDeclineRefundAndCapacityRelease() throws Exception {
        GeneratedApiCredential cred = createMerchantWithApiKey("Merchant Decl", "decl@merchant.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Proc Decl");

        UUID merchantId = cred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        // 1. Merchant creates Refund A = 70000 (capacity remaining: 30000)
        MvcResult createResultA = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-ref-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 70000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundAId = UUID.fromString(JsonPath.read(createResultA.getResponse().getContentAsString(), "$.id"));

        // Merchant cannot create Refund of 50000 (exceeds 30000) -> 409
        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-ref-b-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50000}"))
                .andExpect(status().isConflict());

        // 2. Processor declines Refund A -> 200 DECLINED
        mockMvc.perform(post("/refunds/{id}/decline", refundAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(RefundStatus.DECLINED.name()));

        // 3. Now capacity is freed! Merchant can create Refund B = 100000 -> 201 Created
        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cred.plaintextApiKey())
                        .header("Idempotency-Key", "decl-ref-b-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100000))
                .andExpect(jsonPath("$.status").value(RefundStatus.PENDING.name()));
    }

    // ==========================================
    // 14. SECURITY CROSS-ROLE
    // ==========================================

    @Test
    @DisplayName("Security Cross-Role: Merchant cannot call processor endpoints and Processor cannot call merchant endpoints")
    void shouldEnforceStrictRoleSeparation() throws Exception {
        GeneratedApiCredential merchantCred = createMerchantWithApiKey("Merchant Sec", "sec@merchant.com");
        GeneratedProcessorCredential procCred = createProcessorWithApiKey("Proc Sec");

        UUID merchantId = merchantCred.credential().getMerchantId();
        Payment payment = createApprovedPayment(merchantId, 100000L);

        MvcResult createResult = mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey())
                        .header("Idempotency-Key", "sec-ref-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 20000}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID refundId = UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

        // Merchant -> Processor endpoints -> 403 Forbidden
        mockMvc.perform(post("/refunds/{id}/approve", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/refunds/{id}/decline", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantCred.plaintextApiKey()))
                .andExpect(status().isForbidden());

        // Processor -> Merchant endpoints -> 403 Forbidden
        mockMvc.perform(post("/payments/{paymentId}/refunds", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey())
                        .header("Idempotency-Key", "proc-sec-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 20000}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/refunds/{id}", refundId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + procCred.plaintextApiKey()))
                .andExpect(status().isForbidden());
    }
}
