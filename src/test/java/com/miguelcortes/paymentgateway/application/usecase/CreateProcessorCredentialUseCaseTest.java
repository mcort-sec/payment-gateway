package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateProcessorCredentialCommand;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.dto.GeneratedProcessorCredential;
import com.miguelcortes.paymentgateway.application.exception.DuplicateProcessorKeyPrefixException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorCredentialGenerationException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.ProcessorSuspendedException;
import com.miguelcortes.paymentgateway.application.model.ApiKeyType;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.Processor;
import com.miguelcortes.paymentgateway.domain.model.ProcessorCredential;
import com.miguelcortes.paymentgateway.domain.model.ProcessorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateProcessorCredentialUseCaseTest {

    private InMemoryProcessorCredentialRepository fakeCredentialRepository;
    private InMemoryProcessorRepository fakeProcessorRepository;
    private SequenceApiKeyGenerator fakeApiKeyGenerator;
    private FakeApiKeyHasher fakeApiKeyHasher;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreateProcessorCredentialUseCase useCase;

    private static final UUID ACTIVE_PROCESSOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_PROCESSOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FIXED_CREDENTIAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        fakeCredentialRepository = new InMemoryProcessorCredentialRepository();
        fakeProcessorRepository = new InMemoryProcessorRepository();
        fakeApiKeyGenerator = new SequenceApiKeyGenerator();
        fakeApiKeyHasher = new FakeApiKeyHasher();
        fakeIdGenerator = new FakeIdGenerator(FIXED_CREDENTIAL_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);

        useCase = new CreateProcessorCredentialUseCase(
                fakeCredentialRepository,
                fakeProcessorRepository,
                fakeApiKeyGenerator,
                fakeApiKeyHasher,
                fakeIdGenerator,
                fakeTimeProvider
        );

        Processor activeProcessor = new Processor(
                ACTIVE_PROCESSOR_ID,
                "Processor Active",
                ProcessorStatus.ACTIVE,
                FIXED_TIME
        );
        Processor suspendedProcessor = new Processor(
                SUSPENDED_PROCESSOR_ID,
                "Processor Suspended",
                ProcessorStatus.SUSPENDED,
                FIXED_TIME
        );

        fakeProcessorRepository.save(activeProcessor);
        fakeProcessorRepository.save(suspendedProcessor);
    }

    @Test
    @DisplayName("Should create API credential for ACTIVE processor on first attempt and return plaintext key")
    void shouldCreateProcessorCredentialForActiveProcessor() {
        CreateProcessorCredentialCommand command = new CreateProcessorCredentialCommand(ACTIVE_PROCESSOR_ID);

        GeneratedProcessorCredential result = useCase.execute(command);

        assertNotNull(result);
        assertNotNull(result.credential());
        assertNotNull(result.plaintextApiKey());

        assertEquals(FIXED_CREDENTIAL_ID, result.credential().getId());
        assertEquals(ACTIVE_PROCESSOR_ID, result.credential().getProcessorId());
        assertEquals("prefixSeq001", result.credential().getKeyPrefix());
        assertEquals(String.format("%064x", 1), result.credential().getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, result.credential().getStatus());
        assertEquals(FIXED_TIME, result.credential().getCreatedAt());
        assertNull(result.credential().getRevokedAt());

        assertEquals("pg_proc_test_prefixSeq001_secret001", result.plaintextApiKey());

        // Repository assertions
        assertEquals(1, fakeCredentialRepository.saveCallCount);
        assertSame(result.credential(), fakeCredentialRepository.lastSavedCredential);
        assertFalse(fakeCredentialRepository.lastSavedCredential.getKeyHash().contains("pg_proc_test_prefixSeq001_secret001"),
                "Repository must store hash, never plaintext");
    }

    @Test
    @DisplayName("Should throw ProcessorNotFoundException when processor does not exist and perform no crypto generation")
    void shouldThrowProcessorNotFoundExceptionWhenProcessorDoesNotExist() {
        UUID nonExistentProcessorId = UUID.randomUUID();
        CreateProcessorCredentialCommand command = new CreateProcessorCredentialCommand(nonExistentProcessorId);

        ProcessorNotFoundException exception = assertThrows(
                ProcessorNotFoundException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Processor not found with id: " + nonExistentProcessorId, exception.getMessage());
        assertEquals(0, fakeApiKeyGenerator.callCount);
        assertEquals(0, fakeApiKeyHasher.callCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
        assertEquals(0, fakeCredentialRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw ProcessorSuspendedException when processor is SUSPENDED and perform no crypto generation")
    void shouldThrowProcessorSuspendedExceptionWhenProcessorIsSuspended() {
        CreateProcessorCredentialCommand command = new CreateProcessorCredentialCommand(SUSPENDED_PROCESSOR_ID);

        ProcessorSuspendedException exception = assertThrows(
                ProcessorSuspendedException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Processor is suspended: " + SUSPENDED_PROCESSOR_ID, exception.getMessage());
        assertEquals(0, fakeApiKeyGenerator.callCount);
        assertEquals(0, fakeApiKeyHasher.callCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
        assertEquals(0, fakeCredentialRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should retry with a new key and succeed when first attempt has a prefix collision")
    void shouldRetryAndSucceedOnSecondAttemptWhenFirstCollides() {
        fakeCredentialRepository.simulateCollisionOnNextSaves(1);

        CreateProcessorCredentialCommand command = new CreateProcessorCredentialCommand(ACTIVE_PROCESSOR_ID);

        GeneratedProcessorCredential result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(2, fakeApiKeyGenerator.callCount, "Must generate a second key upon collision");
        assertEquals(2, fakeApiKeyHasher.callCount);
        assertEquals(2, fakeCredentialRepository.saveCallCount);

        assertEquals("prefixSeq002", result.credential().getKeyPrefix());
        assertEquals("pg_proc_test_prefixSeq002_secret002", result.plaintextApiKey());
    }

    @Test
    @DisplayName("Should exhaust retries and throw ProcessorCredentialGenerationException when all 3 attempts collide")
    void shouldThrowProcessorCredentialGenerationExceptionWhenAllRetriesExhausted() {
        fakeCredentialRepository.simulateCollisionOnNextSaves(3);

        CreateProcessorCredentialCommand command = new CreateProcessorCredentialCommand(ACTIVE_PROCESSOR_ID);

        ProcessorCredentialGenerationException exception = assertThrows(
                ProcessorCredentialGenerationException.class,
                () -> useCase.execute(command)
        );

        assertTrue(exception.getMessage().contains("after 3 attempts"));
        assertEquals(3, fakeApiKeyGenerator.callCount);
        assertEquals(3, fakeCredentialRepository.saveCallCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryProcessorRepository implements ProcessorRepositoryPort {
        private final Map<UUID, Processor> storage = new HashMap<>();

        @Override
        public void save(Processor processor) {
            storage.put(processor.getId(), processor);
        }

        @Override
        public Optional<Processor> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }
    }

    private static class InMemoryProcessorCredentialRepository implements ProcessorCredentialRepositoryPort {
        private final Map<UUID, ProcessorCredential> storageById = new HashMap<>();
        private final Map<String, ProcessorCredential> storageByPrefix = new HashMap<>();
        int saveCallCount = 0;
        ProcessorCredential lastSavedCredential;
        private int collisionsToSimulate = 0;

        void simulateCollisionOnNextSaves(int count) {
            this.collisionsToSimulate = count;
        }

        @Override
        public void save(ProcessorCredential credential) {
            saveCallCount++;
            lastSavedCredential = credential;
            if (collisionsToSimulate > 0) {
                collisionsToSimulate--;
                throw new DuplicateProcessorKeyPrefixException("Duplicate processor key prefix: " + credential.getKeyPrefix());
            }
            if (storageByPrefix.containsKey(credential.getKeyPrefix())) {
                throw new DuplicateProcessorKeyPrefixException("Duplicate processor key prefix: " + credential.getKeyPrefix());
            }
            storageById.put(credential.getId(), credential);
            storageByPrefix.put(credential.getKeyPrefix(), credential);
        }

        @Override
        public Optional<ProcessorCredential> findById(UUID id) {
            return Optional.ofNullable(storageById.get(id));
        }

        @Override
        public Optional<ProcessorCredential> findByKeyPrefix(String keyPrefix) {
            return Optional.ofNullable(storageByPrefix.get(keyPrefix));
        }
    }

    private static class SequenceApiKeyGenerator implements ApiKeyGeneratorPort {
        int callCount = 0;

        @Override
        public GeneratedApiKey generate(ApiKeyType type) {
            callCount++;
            String prefix = String.format("prefixSeq%03d", callCount);
            String secret = String.format("secret%03d", callCount);
            return new GeneratedApiKey(prefix, "pg_proc_test_" + prefix + "_" + secret);
        }
    }

    private static class FakeApiKeyHasher implements ApiKeyHasherPort {
        int callCount = 0;

        @Override
        public String hash(String plaintextApiKey) {
            callCount++;
            return String.format("%064x", callCount);
        }

        @Override
        public boolean verify(String plaintextApiKey, String expectedHash) {
            throw new UnsupportedOperationException("verify is not used in CreateProcessorCredentialUseCase");
        }
    }

    private static class FakeIdGenerator implements IdGenerator {
        private final UUID fixedId;
        int callCount = 0;

        FakeIdGenerator(UUID fixedId) {
            this.fixedId = fixedId;
        }

        @Override
        public UUID generate() {
            callCount++;
            return fixedId;
        }
    }

    private static class FakeTimeProvider implements TimeProvider {
        private final Instant fixedTime;
        int callCount = 0;

        FakeTimeProvider(Instant fixedTime) {
            this.fixedTime = fixedTime;
        }

        @Override
        public Instant now() {
            callCount++;
            return fixedTime;
        }
    }
}
