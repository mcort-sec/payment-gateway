package com.miguelcortes.paymentgateway.infrastructure.demo;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import com.miguelcortes.paymentgateway.infrastructure.security.ApiKeyParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("demo")
class DemoDataBootstrapTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DemoDataBootstrap bootstrap;

    @Autowired
    private MerchantRepositoryPort merchantRepository;

    @Autowired
    private ProcessorRepositoryPort processorRepository;

    @Autowired
    private ApiCredentialRepositoryPort apiCredentialRepository;

    @Autowired
    private ProcessorCredentialRepositoryPort processorCredentialRepository;

    @Autowired
    private ApiKeyHasherPort apiKeyHasher;

    @Autowired
    private ApiKeyParser apiKeyParser;

    @Test
    @DisplayName("Should bootstrap 2 merchants, 2 processors, and 4 credentials with valid statuses and hashes")
    void shouldBootstrapDemoActorsAndCredentials() {
        // 1. Verify Active Merchant
        Optional<Merchant> activeMerchant = merchantRepository.findById(DemoDataBootstrap.ACTIVE_MERCHANT_ID);
        assertTrue(activeMerchant.isPresent());
        assertEquals(MerchantStatus.ACTIVE, activeMerchant.get().getStatus());
        assertEquals("active-merchant@paymentgateway.demo", activeMerchant.get().getEmail());

        // 2. Verify Suspended Merchant
        Optional<Merchant> suspendedMerchant = merchantRepository.findById(DemoDataBootstrap.SUSPENDED_MERCHANT_ID);
        assertTrue(suspendedMerchant.isPresent());
        assertEquals(MerchantStatus.SUSPENDED, suspendedMerchant.get().getStatus());
        assertEquals("suspended-merchant@paymentgateway.demo", suspendedMerchant.get().getEmail());

        // 3. Verify Active Processor
        Optional<Processor> activeProcessor = processorRepository.findById(DemoDataBootstrap.ACTIVE_PROCESSOR_ID);
        assertTrue(activeProcessor.isPresent());
        assertEquals(ProcessorStatus.ACTIVE, activeProcessor.get().getStatus());
        assertEquals("Demo Active Processor", activeProcessor.get().getName());

        // 4. Verify Suspended Processor
        Optional<Processor> suspendedProcessor = processorRepository.findById(DemoDataBootstrap.SUSPENDED_PROCESSOR_ID);
        assertTrue(suspendedProcessor.isPresent());
        assertEquals(ProcessorStatus.SUSPENDED, suspendedProcessor.get().getStatus());
        assertEquals("Demo Suspended Processor", suspendedProcessor.get().getName());

        // 5. Verify Active Merchant Credential & Hash
        Optional<ApiCredential> activeMercCred = apiCredentialRepository.findByKeyPrefix(DemoDataBootstrap.ACTIVE_MERCHANT_PREFIX);
        assertTrue(activeMercCred.isPresent());
        assertEquals(DemoDataBootstrap.ACTIVE_MERCHANT_ID, activeMercCred.get().getMerchantId());
        assertTrue(apiKeyHasher.verify(DemoDataBootstrap.ACTIVE_MERCHANT_KEY, activeMercCred.get().getKeyHash()));

        // 6. Verify Suspended Merchant Credential & Hash
        Optional<ApiCredential> suspendedMercCred = apiCredentialRepository.findByKeyPrefix(DemoDataBootstrap.SUSPENDED_MERCHANT_PREFIX);
        assertTrue(suspendedMercCred.isPresent());
        assertEquals(DemoDataBootstrap.SUSPENDED_MERCHANT_ID, suspendedMercCred.get().getMerchantId());
        assertTrue(apiKeyHasher.verify(DemoDataBootstrap.SUSPENDED_MERCHANT_KEY, suspendedMercCred.get().getKeyHash()));

        // 7. Verify Active Processor Credential & Hash
        Optional<ProcessorCredential> activeProcCred = processorCredentialRepository.findByKeyPrefix(DemoDataBootstrap.ACTIVE_PROCESSOR_PREFIX);
        assertTrue(activeProcCred.isPresent());
        assertEquals(DemoDataBootstrap.ACTIVE_PROCESSOR_ID, activeProcCred.get().getProcessorId());
        assertTrue(apiKeyHasher.verify(DemoDataBootstrap.ACTIVE_PROCESSOR_KEY, activeProcCred.get().getKeyHash()));

        // 8. Verify Suspended Processor Credential & Hash
        Optional<ProcessorCredential> suspendedProcCred = processorCredentialRepository.findByKeyPrefix(DemoDataBootstrap.SUSPENDED_PROCESSOR_PREFIX);
        assertTrue(suspendedProcCred.isPresent());
        assertEquals(DemoDataBootstrap.SUSPENDED_PROCESSOR_ID, suspendedProcCred.get().getProcessorId());
        assertTrue(apiKeyHasher.verify(DemoDataBootstrap.SUSPENDED_PROCESSOR_KEY, suspendedProcCred.get().getKeyHash()));
    }

    @Test
    @DisplayName("Should parse and accept all four demo plaintext API keys with ApiKeyParser")
    void shouldParseAllFourDemoPlaintextApiKeys() {
        Optional<ApiKeyParser.ParsedApiKey> parsedActiveMerc =
                apiKeyParser.parseHeader("Bearer " + DemoDataBootstrap.ACTIVE_MERCHANT_KEY);
        assertTrue(parsedActiveMerc.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Merchant.class, parsedActiveMerc.get());
        assertEquals(DemoDataBootstrap.ACTIVE_MERCHANT_PREFIX, parsedActiveMerc.get().keyPrefix());
        assertEquals(DemoDataBootstrap.ACTIVE_MERCHANT_KEY, parsedActiveMerc.get().plaintextKey());

        Optional<ApiKeyParser.ParsedApiKey> parsedSuspendedMerc =
                apiKeyParser.parseHeader("Bearer " + DemoDataBootstrap.SUSPENDED_MERCHANT_KEY);
        assertTrue(parsedSuspendedMerc.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Merchant.class, parsedSuspendedMerc.get());
        assertEquals(DemoDataBootstrap.SUSPENDED_MERCHANT_PREFIX, parsedSuspendedMerc.get().keyPrefix());
        assertEquals(DemoDataBootstrap.SUSPENDED_MERCHANT_KEY, parsedSuspendedMerc.get().plaintextKey());

        Optional<ApiKeyParser.ParsedApiKey> parsedActiveProc =
                apiKeyParser.parseHeader("Bearer " + DemoDataBootstrap.ACTIVE_PROCESSOR_KEY);
        assertTrue(parsedActiveProc.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Processor.class, parsedActiveProc.get());
        assertEquals(DemoDataBootstrap.ACTIVE_PROCESSOR_PREFIX, parsedActiveProc.get().keyPrefix());
        assertEquals(DemoDataBootstrap.ACTIVE_PROCESSOR_KEY, parsedActiveProc.get().plaintextKey());

        Optional<ApiKeyParser.ParsedApiKey> parsedSuspendedProc =
                apiKeyParser.parseHeader("Bearer " + DemoDataBootstrap.SUSPENDED_PROCESSOR_KEY);
        assertTrue(parsedSuspendedProc.isPresent());
        assertInstanceOf(ApiKeyParser.ParsedApiKey.Processor.class, parsedSuspendedProc.get());
        assertEquals(DemoDataBootstrap.SUSPENDED_PROCESSOR_PREFIX, parsedSuspendedProc.get().keyPrefix());
        assertEquals(DemoDataBootstrap.SUSPENDED_PROCESSOR_KEY, parsedSuspendedProc.get().plaintextKey());
    }

    @Test
    @DisplayName("Should be idempotent when run() is executed repeatedly")
    void shouldBeIdempotentOnRepeatedRuns() {
        // Run bootstrap a second and third time
        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        // Verify counts / state remain intact
        Optional<Merchant> activeMerchant = merchantRepository.findById(DemoDataBootstrap.ACTIVE_MERCHANT_ID);
        assertTrue(activeMerchant.isPresent());
        assertEquals(MerchantStatus.ACTIVE, activeMerchant.get().getStatus());

        Optional<Merchant> suspendedMerchant = merchantRepository.findById(DemoDataBootstrap.SUSPENDED_MERCHANT_ID);
        assertTrue(suspendedMerchant.isPresent());
        assertEquals(MerchantStatus.SUSPENDED, suspendedMerchant.get().getStatus());

        Optional<Processor> activeProcessor = processorRepository.findById(DemoDataBootstrap.ACTIVE_PROCESSOR_ID);
        assertTrue(activeProcessor.isPresent());
        assertEquals(ProcessorStatus.ACTIVE, activeProcessor.get().getStatus());

        Optional<Processor> suspendedProcessor = processorRepository.findById(DemoDataBootstrap.SUSPENDED_PROCESSOR_ID);
        assertTrue(suspendedProcessor.isPresent());
        assertEquals(ProcessorStatus.SUSPENDED, suspendedProcessor.get().getStatus());
    }

    private static void assertInstanceOf(Class<?> expectedType, Object actual) {
        assertNotNull(actual);
        assertTrue(expectedType.isInstance(actual), "Expected instance of " + expectedType.getName() + " but got " + actual.getClass().getName());
    }
}
