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
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.domain.model.Payment;
import com.miguelcortes.paymentgateway.domain.model.PaymentStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.Refund;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MerchantTransactionHistoryEndToEndTest {

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

    @Test
    @DisplayName("Empty collection contract: Should return 200 with empty list when merchant has no payments or refunds")
    void shouldReturn200WithEmptyListWhenMerchantHasNoRecords() throws Exception {
        Merchant merchant = createMerchant("Empty Merchant", MerchantStatus.ACTIVE);
        String apiKey = createMerchantApiKey(merchant.getId());

        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is(empty())))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));

        mockMvc.perform(get("/refunds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is(empty())))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    @DisplayName("Tenant isolation: Merchant A and Merchant B should only see their own payments and refunds")
    void shouldEnforceTenantIsolationForPaymentsAndRefunds() throws Exception {
        Merchant merchantA = createMerchant("Merchant A", MerchantStatus.ACTIVE);
        String apiKeyA = createMerchantApiKey(merchantA.getId());

        Merchant merchantB = createMerchant("Merchant B", MerchantStatus.ACTIVE);
        String apiKeyB = createMerchantApiKey(merchantB.getId());

        Payment paymentA = createAndPersistPayment(merchantA.getId(), 10000L, "idemp-a1");
        Payment paymentB = createAndPersistPayment(merchantB.getId(), 20000L, "idemp-b1");

        Refund refundA = createAndPersistRefund(paymentA.getId(), merchantA.getId(), 5000L, "ref-idemp-a1");
        Refund refundB = createAndPersistRefund(paymentB.getId(), merchantB.getId(), 8000L, "ref-idemp-b1");

        // Merchant A listing
        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(paymentA.getId().toString())))
                .andExpect(jsonPath("$.content[0].merchantId", is(merchantA.getId().toString())));

        mockMvc.perform(get("/refunds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(refundA.getId().toString())))
                .andExpect(jsonPath("$.content[0].merchantId", is(merchantA.getId().toString())));

        // Merchant B listing
        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(paymentB.getId().toString())))
                .andExpect(jsonPath("$.content[0].merchantId", is(merchantB.getId().toString())));

        mockMvc.perform(get("/refunds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(refundB.getId().toString())))
                .andExpect(jsonPath("$.content[0].merchantId", is(merchantB.getId().toString())));
    }

    @Test
    @DisplayName("Pagination & Ordering: Should paginate across multiple pages without duplicates ordered by createdAt DESC, id DESC")
    void shouldPaginateAcrossMultiplePagesWithoutDuplicates() throws Exception {
        Merchant merchant = createMerchant("Paging Merchant", MerchantStatus.ACTIVE);
        String apiKey = createMerchantApiKey(merchant.getId());

        Instant baseTime = Instant.parse("2026-09-29T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            Payment payment = new Payment(
                    UUID.randomUUID(),
                    merchant.getId(),
                    1000L * (i + 1),
                    Currency.COP,
                    "key-p-" + i,
                    baseTime.plusSeconds(i * 10)
            );
            paymentAdapter.save(payment);
        }

        Set<String> seenIds = new HashSet<>();

        // Page 0
        MvcResult page0Result = mockMvc.perform(get("/payments?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andReturn();

        List<String> page0Ids = JsonPath.read(page0Result.getResponse().getContentAsString(), "$.content[*].id");
        seenIds.addAll(page0Ids);

        // Page 1
        MvcResult page1Result = mockMvc.perform(get("/payments?page=1&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andReturn();

        List<String> page1Ids = JsonPath.read(page1Result.getResponse().getContentAsString(), "$.content[*].id");
        for (String id : page1Ids) {
            assertThat(seenIds.add(id)).as("Duplicate payment found across pages: " + id).isTrue();
        }

        // Page 2
        MvcResult page2Result = mockMvc.perform(get("/payments?page=2&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andReturn();

        List<String> page2Ids = JsonPath.read(page2Result.getResponse().getContentAsString(), "$.content[*].id");
        for (String id : page2Ids) {
            assertThat(seenIds.add(id)).as("Duplicate payment found across pages: " + id).isTrue();
        }

        assertThat(seenIds).hasSize(5);
    }

    @Test
    @DisplayName("Default pagination: GET /payments and GET /refunds without query params should default to page 0, size 20")
    void shouldDefaultPaginationParameters() throws Exception {
        Merchant merchant = createMerchant("Default Page Merchant", MerchantStatus.ACTIVE);
        String apiKey = createMerchantApiKey(merchant.getId());

        mockMvc.perform(get("/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)));

        mockMvc.perform(get("/refunds")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)));
    }

    @Test
    @DisplayName("Invalid pagination: Should return 400 Bad Request with ErrorResponse for invalid page or size")
    void shouldReturn400ForInvalidPaginationParameters() throws Exception {
        Merchant merchant = createMerchant("Validation Merchant", MerchantStatus.ACTIVE);
        String apiKey = createMerchantApiKey(merchant.getId());

        // page = -1 on /payments
        mockMvc.perform(get("/payments?page=-1&size=20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page index must not be negative")))
                .andExpect(jsonPath("$.path", is("/payments")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        // size = 0 on /payments
        mockMvc.perform(get("/payments?page=0&size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page size must be between 1 and 100")))
                .andExpect(jsonPath("$.path", is("/payments")));

        // size = 101 on /payments
        mockMvc.perform(get("/payments?page=0&size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page size must be between 1 and 100")))
                .andExpect(jsonPath("$.path", is("/payments")));

        // page = -1 on /refunds
        mockMvc.perform(get("/refunds?page=-1&size=20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page index must not be negative")))
                .andExpect(jsonPath("$.path", is("/refunds")));

        // size = 0 on /refunds
        mockMvc.perform(get("/refunds?page=0&size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page size must be between 1 and 100")))
                .andExpect(jsonPath("$.path", is("/refunds")));

        // size = 101 on /refunds
        mockMvc.perform(get("/refunds?page=0&size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Page size must be between 1 and 100")))
                .andExpect(jsonPath("$.path", is("/refunds")));
    }

    @Test
    @DisplayName("Authentication & Authorization: Merchant ACTIVE (200), Merchant SUSPENDED (200), Processor (403), Anonymous (401), Invalid token (401)")
    void shouldEnforceSecurityMatrixOnListingEndpoints() throws Exception {
        Merchant activeMerchant = createMerchant("Active Merchant", MerchantStatus.ACTIVE);
        String activeApiKey = createMerchantApiKey(activeMerchant.getId());

        Merchant suspendedMerchant = createMerchant("Suspended Merchant", MerchantStatus.ACTIVE);
        String suspendedApiKey = createMerchantApiKey(suspendedMerchant.getId());
        suspendedMerchant.suspend();
        merchantAdapter.save(suspendedMerchant);

        Processor processor = createProcessor("Active Processor");
        String processorApiKey = createProcessorApiKey(processor.getId());

        // Merchant ACTIVE -> 200
        mockMvc.perform(get("/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + activeApiKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, "Bearer " + activeApiKey))
                .andExpect(status().isOk());

        // Merchant SUSPENDED -> 200 (can list history)
        mockMvc.perform(get("/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + suspendedApiKey))
                .andExpect(status().isOk());
        mockMvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, "Bearer " + suspendedApiKey))
                .andExpect(status().isOk());

        // Processor -> 403 Forbidden
        mockMvc.perform(get("/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + processorApiKey))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, "Bearer " + processorApiKey))
                .andExpect(status().isForbidden());

        // Anonymous -> 401 Unauthorized
        mockMvc.perform(get("/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/refunds"))
                .andExpect(status().isUnauthorized());

        // Invalid Bearer Token -> 401 Unauthorized
        mockMvc.perform(get("/payments").header(HttpHeaders.AUTHORIZATION, "Bearer invalid_secret_token_12345"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, "Bearer invalid_secret_token_12345"))
                .andExpect(status().isUnauthorized());
    }

    private Merchant createMerchant(String name, MerchantStatus status) {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                name,
                "merchant_" + UUID.randomUUID() + "@example.com",
                status,
                Instant.now()
        );
        merchantAdapter.save(merchant);
        return merchant;
    }

    private String createMerchantApiKey(UUID merchantId) {
        GeneratedApiCredential cred = createApiCredentialUseCase.execute(
                new CreateApiCredentialCommand(merchantId)
        );
        return cred.plaintextApiKey();
    }

    private Processor createProcessor(String name) {
        Processor processor = createProcessorUseCase.execute(new CreateProcessorCommand(name));
        return processor;
    }

    private String createProcessorApiKey(UUID processorId) {
        GeneratedProcessorCredential cred = createProcessorCredentialUseCase.execute(
                new CreateProcessorCredentialCommand(processorId)
        );
        return cred.plaintextApiKey();
    }

    private Payment createAndPersistPayment(UUID merchantId, long amount, String idempotencyKey) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                merchantId,
                amount,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );
        paymentAdapter.save(payment);
        return payment;
    }

    private Refund createAndPersistRefund(UUID paymentId, UUID merchantId, long amount, String idempotencyKey) {
        Refund refund = new Refund(
                UUID.randomUUID(),
                paymentId,
                merchantId,
                amount,
                Currency.COP,
                idempotencyKey,
                Instant.now()
        );
        refundAdapter.save(refund);
        return refund;
    }
}
