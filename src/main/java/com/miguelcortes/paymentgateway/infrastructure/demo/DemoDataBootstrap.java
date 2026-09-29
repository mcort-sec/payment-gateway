package com.miguelcortes.paymentgateway.infrastructure.demo;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Profile("demo")
public class DemoDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    public static final UUID ACTIVE_MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID SUSPENDED_MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID ACTIVE_PROCESSOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID SUSPENDED_PROCESSOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    public static final UUID ACTIVE_MERCHANT_CRED_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    public static final UUID SUSPENDED_MERCHANT_CRED_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    public static final UUID ACTIVE_PROCESSOR_CRED_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");
    public static final UUID SUSPENDED_PROCESSOR_CRED_ID = UUID.fromString("00000000-0000-0000-0000-000000000014");

    public static final String ACTIVE_MERCHANT_NAME = "Demo Active Merchant";
    public static final String ACTIVE_MERCHANT_EMAIL = "active-merchant@paymentgateway.demo";
    public static final String ACTIVE_MERCHANT_PREFIX = "demoActvMerc";
    public static final String ACTIVE_MERCHANT_KEY = "pg_test_demoActvMerc_ActiveMerchantDemoSecretKeyForTestingV12345";

    public static final String SUSPENDED_MERCHANT_NAME = "Demo Suspended Merchant";
    public static final String SUSPENDED_MERCHANT_EMAIL = "suspended-merchant@paymentgateway.demo";
    public static final String SUSPENDED_MERCHANT_PREFIX = "demoSuspMerc";
    public static final String SUSPENDED_MERCHANT_KEY = "pg_test_demoSuspMerc_SuspendedMerchantDemoSecretKeyTestingV12345";

    public static final String ACTIVE_PROCESSOR_NAME = "Demo Active Processor";
    public static final String ACTIVE_PROCESSOR_PREFIX = "demoActvProc";
    public static final String ACTIVE_PROCESSOR_KEY = "pg_proc_test_demoActvProc_ActiveProcessorDemoSecretKeyForTestingV1234";

    public static final String SUSPENDED_PROCESSOR_NAME = "Demo Suspended Processor";
    public static final String SUSPENDED_PROCESSOR_PREFIX = "demoSuspProc";
    public static final String SUSPENDED_PROCESSOR_KEY = "pg_proc_test_demoSuspProc_SuspendedProcessorDemoSecretKeyTestingV1234";

    private final MerchantRepositoryPort merchantRepositoryPort;
    private final ProcessorRepositoryPort processorRepositoryPort;
    private final ApiCredentialRepositoryPort apiCredentialRepositoryPort;
    private final ProcessorCredentialRepositoryPort processorCredentialRepositoryPort;
    private final ApiKeyHasherPort apiKeyHasher;
    private final TimeProvider timeProvider;

    public DemoDataBootstrap(
            MerchantRepositoryPort merchantRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort,
            ApiCredentialRepositoryPort apiCredentialRepositoryPort,
            ProcessorCredentialRepositoryPort processorCredentialRepositoryPort,
            ApiKeyHasherPort apiKeyHasher,
            TimeProvider timeProvider
    ) {
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.processorRepositoryPort = processorRepositoryPort;
        this.apiCredentialRepositoryPort = apiCredentialRepositoryPort;
        this.processorCredentialRepositoryPort = processorCredentialRepositoryPort;
        this.apiKeyHasher = apiKeyHasher;
        this.timeProvider = timeProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = timeProvider.now();

        bootstrapActiveMerchant(now);
        bootstrapSuspendedMerchant(now);
        bootstrapActiveProcessor(now);
        bootstrapSuspendedProcessor(now);

        printDemoBanner();
    }

    private void bootstrapActiveMerchant(Instant now) {
        Optional<Merchant> merchantOpt = merchantRepositoryPort.findById(ACTIVE_MERCHANT_ID);
        if (merchantOpt.isEmpty()) {
            Merchant merchant = new Merchant(
                    ACTIVE_MERCHANT_ID,
                    ACTIVE_MERCHANT_NAME,
                    ACTIVE_MERCHANT_EMAIL,
                    MerchantStatus.ACTIVE,
                    now
            );
            merchantRepositoryPort.save(merchant);
        } else {
            Merchant merchant = merchantOpt.get();
            if (!merchant.isActive()) {
                merchant.activate();
                merchantRepositoryPort.save(merchant);
            }
        }

        if (apiCredentialRepositoryPort.findByKeyPrefix(ACTIVE_MERCHANT_PREFIX).isEmpty()) {
            String hash = apiKeyHasher.hash(ACTIVE_MERCHANT_KEY);
            ApiCredential credential = new ApiCredential(
                    ACTIVE_MERCHANT_CRED_ID,
                    ACTIVE_MERCHANT_ID,
                    ACTIVE_MERCHANT_PREFIX,
                    hash,
                    now
            );
            apiCredentialRepositoryPort.save(credential);
        }
    }

    private void bootstrapSuspendedMerchant(Instant now) {
        Optional<Merchant> merchantOpt = merchantRepositoryPort.findById(SUSPENDED_MERCHANT_ID);
        if (merchantOpt.isEmpty()) {
            Merchant merchant = new Merchant(
                    SUSPENDED_MERCHANT_ID,
                    SUSPENDED_MERCHANT_NAME,
                    SUSPENDED_MERCHANT_EMAIL,
                    MerchantStatus.SUSPENDED,
                    now
            );
            merchantRepositoryPort.save(merchant);
        } else {
            Merchant merchant = merchantOpt.get();
            if (merchant.isActive()) {
                merchant.suspend();
                merchantRepositoryPort.save(merchant);
            }
        }

        if (apiCredentialRepositoryPort.findByKeyPrefix(SUSPENDED_MERCHANT_PREFIX).isEmpty()) {
            String hash = apiKeyHasher.hash(SUSPENDED_MERCHANT_KEY);
            ApiCredential credential = new ApiCredential(
                    SUSPENDED_MERCHANT_CRED_ID,
                    SUSPENDED_MERCHANT_ID,
                    SUSPENDED_MERCHANT_PREFIX,
                    hash,
                    now
            );
            apiCredentialRepositoryPort.save(credential);
        }
    }

    private void bootstrapActiveProcessor(Instant now) {
        Optional<Processor> processorOpt = processorRepositoryPort.findById(ACTIVE_PROCESSOR_ID);
        if (processorOpt.isEmpty()) {
            Processor processor = new Processor(
                    ACTIVE_PROCESSOR_ID,
                    ACTIVE_PROCESSOR_NAME,
                    ProcessorStatus.ACTIVE,
                    now
            );
            processorRepositoryPort.save(processor);
        } else {
            Processor processor = processorOpt.get();
            if (!processor.isActive()) {
                processor.activate();
                processorRepositoryPort.save(processor);
            }
        }

        if (processorCredentialRepositoryPort.findByKeyPrefix(ACTIVE_PROCESSOR_PREFIX).isEmpty()) {
            String hash = apiKeyHasher.hash(ACTIVE_PROCESSOR_KEY);
            ProcessorCredential credential = new ProcessorCredential(
                    ACTIVE_PROCESSOR_CRED_ID,
                    ACTIVE_PROCESSOR_ID,
                    ACTIVE_PROCESSOR_PREFIX,
                    hash,
                    now
            );
            processorCredentialRepositoryPort.save(credential);
        }
    }

    private void bootstrapSuspendedProcessor(Instant now) {
        Optional<Processor> processorOpt = processorRepositoryPort.findById(SUSPENDED_PROCESSOR_ID);
        if (processorOpt.isEmpty()) {
            Processor processor = new Processor(
                    SUSPENDED_PROCESSOR_ID,
                    SUSPENDED_PROCESSOR_NAME,
                    ProcessorStatus.SUSPENDED,
                    now
            );
            processorRepositoryPort.save(processor);
        } else {
            Processor processor = processorOpt.get();
            if (processor.isActive()) {
                processor.suspend();
                processorRepositoryPort.save(processor);
            }
        }

        if (processorCredentialRepositoryPort.findByKeyPrefix(SUSPENDED_PROCESSOR_PREFIX).isEmpty()) {
            String hash = apiKeyHasher.hash(SUSPENDED_PROCESSOR_KEY);
            ProcessorCredential credential = new ProcessorCredential(
                    SUSPENDED_PROCESSOR_CRED_ID,
                    SUSPENDED_PROCESSOR_ID,
                    SUSPENDED_PROCESSOR_PREFIX,
                    hash,
                    now
            );
            processorCredentialRepositoryPort.save(credential);
        }
    }

    private void printDemoBanner() {
        String banner = """

                ================================================================================
                PAYMENT GATEWAY — DEMO CREDENTIALS LOADED
                ================================================================================
                [PROFILE: demo] Use these credentials to test the API locally via Postman / cURL.

                1. ACTIVE MERCHANT:
                   Merchant ID : %s
                   API Key     : %s
                   Permissions : Create / Get / List Payments, Cancel Payments, Create / Get / List Refunds

                2. SUSPENDED MERCHANT:
                   Merchant ID : %s
                   API Key     : %s
                   Permissions : Get / List resources, Cancel PENDING Payments, Create Refunds; Payment Creation returns 403 Forbidden

                3. ACTIVE PROCESSOR:
                   Processor ID: %s
                   API Key     : %s
                   Permissions : Approve / Decline Payments and Refunds

                4. SUSPENDED PROCESSOR:
                   Processor ID: %s
                   API Key     : %s
                   Permissions : Callback operations return 403 Forbidden
                ================================================================================
                DEMO PROFILE ONLY — NEVER USE THESE CREDENTIALS IN PRODUCTION
                ================================================================================
                """.formatted(
                ACTIVE_MERCHANT_ID, ACTIVE_MERCHANT_KEY,
                SUSPENDED_MERCHANT_ID, SUSPENDED_MERCHANT_KEY,
                ACTIVE_PROCESSOR_ID, ACTIVE_PROCESSOR_KEY,
                SUSPENDED_PROCESSOR_ID, SUSPENDED_PROCESSOR_KEY
        );

        System.out.println(banner);
        log.info("Demo credentials bootstrapped successfully for active/suspended merchants and processors.");
    }
}
