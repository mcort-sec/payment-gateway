package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateApiCredentialCommand;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiCredential;
import com.miguelcortes.paymentgateway.application.dto.GeneratedApiKey;
import com.miguelcortes.paymentgateway.application.exception.ApiCredentialGenerationException;
import com.miguelcortes.paymentgateway.application.exception.DuplicateKeyPrefixException;
import com.miguelcortes.paymentgateway.application.exception.MerchantNotFoundException;
import com.miguelcortes.paymentgateway.application.exception.MerchantSuspendedException;
import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

class CreateApiCredentialUseCaseTest {

    private InMemoryApiCredentialRepository fakeCredentialRepository;
    private InMemoryMerchantRepository fakeMerchantRepository;
    private SequenceApiKeyGenerator fakeApiKeyGenerator;
    private FakeApiKeyHasher fakeApiKeyHasher;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreateApiCredentialUseCase useCase;

    private static final UUID ACTIVE_MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUSPENDED_MERCHANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FIXED_CREDENTIAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        fakeCredentialRepository = new InMemoryApiCredentialRepository();
        fakeMerchantRepository = new InMemoryMerchantRepository();
        fakeApiKeyGenerator = new SequenceApiKeyGenerator();
        fakeApiKeyHasher = new FakeApiKeyHasher();
        fakeIdGenerator = new FakeIdGenerator(FIXED_CREDENTIAL_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);

        useCase = new CreateApiCredentialUseCase(
                fakeCredentialRepository,
                fakeMerchantRepository,
                fakeApiKeyGenerator,
                fakeApiKeyHasher,
                fakeIdGenerator,
                fakeTimeProvider
        );

        Merchant activeMerchant = new Merchant(
                ACTIVE_MERCHANT_ID,
                "Acme Active",
                "active@acme.com",
                MerchantStatus.ACTIVE,
                FIXED_TIME
        );
        Merchant suspendedMerchant = new Merchant(
                SUSPENDED_MERCHANT_ID,
                "Acme Suspended",
                "suspended@acme.com",
                MerchantStatus.SUSPENDED,
                FIXED_TIME
        );

        fakeMerchantRepository.save(activeMerchant);
        fakeMerchantRepository.save(suspendedMerchant);
    }

    @Test
    @DisplayName("Should create API credential for ACTIVE merchant on first attempt and return plaintext key")
    void shouldCreateApiCredentialForActiveMerchant() {
        CreateApiCredentialCommand command = new CreateApiCredentialCommand(ACTIVE_MERCHANT_ID);

        GeneratedApiCredential result = useCase.execute(command);

        assertNotNull(result);
        assertNotNull(result.credential());
        assertNotNull(result.plaintextApiKey());

        assertEquals(FIXED_CREDENTIAL_ID, result.credential().getId());
        assertEquals(ACTIVE_MERCHANT_ID, result.credential().getMerchantId());
        assertEquals("prefixSeq001", result.credential().getKeyPrefix());
        assertEquals(String.format("%064x", 1), result.credential().getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, result.credential().getStatus());
        assertEquals(FIXED_TIME, result.credential().getCreatedAt());
        assertNull(result.credential().getRevokedAt());

        assertEquals("pg_test_prefixSeq001_secret001", result.plaintextApiKey());

        // Repository assertions
        assertEquals(1, fakeCredentialRepository.saveCallCount);
        assertSame(result.credential(), fakeCredentialRepository.lastSavedCredential);
        assertFalse(fakeCredentialRepository.lastSavedCredential.getKeyHash().contains("pg_test_prefixSeq001_secret001"),
                "Repository must store hash, never plaintext");
    }

    @Test
    @DisplayName("Should throw MerchantNotFoundException when merchant does not exist and perform no crypto generation")
    void shouldThrowMerchantNotFoundExceptionWhenMerchantDoesNotExist() {
        UUID nonExistentMerchantId = UUID.randomUUID();
        CreateApiCredentialCommand command = new CreateApiCredentialCommand(nonExistentMerchantId);

        MerchantNotFoundException exception = assertThrows(
                MerchantNotFoundException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Merchant not found with id: " + nonExistentMerchantId, exception.getMessage());
        assertEquals(0, fakeApiKeyGenerator.callCount);
        assertEquals(0, fakeApiKeyHasher.callCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
        assertEquals(0, fakeCredentialRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should throw MerchantSuspendedException when merchant is SUSPENDED and perform no crypto generation")
    void shouldThrowMerchantSuspendedExceptionWhenMerchantIsSuspended() {
        CreateApiCredentialCommand command = new CreateApiCredentialCommand(SUSPENDED_MERCHANT_ID);

        MerchantSuspendedException exception = assertThrows(
                MerchantSuspendedException.class,
                () -> useCase.execute(command)
        );

        assertEquals("Merchant is suspended: " + SUSPENDED_MERCHANT_ID, exception.getMessage());
        assertEquals(0, fakeApiKeyGenerator.callCount);
        assertEquals(0, fakeApiKeyHasher.callCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
        assertEquals(0, fakeCredentialRepository.saveCallCount);
    }

    @Test
    @DisplayName("Should retry with a new key and succeed when first attempt has a prefix collision")
    void shouldRetryAndSucceedOnSecondAttemptWhenFirstCollides() {
        // Simulate collision on first save attempt
        fakeCredentialRepository.simulateCollisionOnNextSaves(1);

        CreateApiCredentialCommand command = new CreateApiCredentialCommand(ACTIVE_MERCHANT_ID);

        GeneratedApiCredential result = useCase.execute(command);

        assertNotNull(result);
        assertEquals(2, fakeApiKeyGenerator.callCount, "Must generate a second key upon collision");
        assertEquals(2, fakeApiKeyHasher.callCount);
        assertEquals(2, fakeCredentialRepository.saveCallCount);

        // Result corresponds to the second generated key
        assertEquals("prefixSeq002", result.credential().getKeyPrefix());
        assertEquals("pg_test_prefixSeq002_secret002", result.plaintextApiKey());
    }

    @Test
    @DisplayName("Should exhaust retries and throw ApiCredentialGenerationException when all 3 attempts collide")
    void shouldThrowApiCredentialGenerationExceptionWhenAllRetriesExhausted() {
        // Simulate 3 consecutive collisions
        fakeCredentialRepository.simulateCollisionOnNextSaves(3);

        CreateApiCredentialCommand command = new CreateApiCredentialCommand(ACTIVE_MERCHANT_ID);

        ApiCredentialGenerationException exception = assertThrows(
                ApiCredentialGenerationException.class,
                () -> useCase.execute(command)
        );

        assertTrue(exception.getMessage().contains("after 3 attempts"));
        assertEquals(3, fakeApiKeyGenerator.callCount);
        assertEquals(3, fakeCredentialRepository.saveCallCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryMerchantRepository implements MerchantRepositoryPort {
        private final Map<UUID, Merchant> storage = new HashMap<>();

        @Override
        public void save(Merchant merchant) {
            storage.put(merchant.getId(), merchant);
        }

        @Override
        public Optional<Merchant> findById(UUID id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Merchant> findByEmail(String email) {
            return storage.values().stream()
                    .filter(m -> m.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        }
    }

    private static class InMemoryApiCredentialRepository implements ApiCredentialRepositoryPort {
        private final Map<UUID, ApiCredential> storageById = new HashMap<>();
        private final Map<String, ApiCredential> storageByPrefix = new HashMap<>();
        int saveCallCount = 0;
        ApiCredential lastSavedCredential;
        private int collisionsToSimulate = 0;

        void simulateCollisionOnNextSaves(int count) {
            this.collisionsToSimulate = count;
        }

        @Override
        public void save(ApiCredential credential) {
            saveCallCount++;
            lastSavedCredential = credential;
            if (collisionsToSimulate > 0) {
                collisionsToSimulate--;
                throw new DuplicateKeyPrefixException("Duplicate key prefix: " + credential.getKeyPrefix());
            }
            if (storageByPrefix.containsKey(credential.getKeyPrefix())) {
                throw new DuplicateKeyPrefixException("Duplicate key prefix: " + credential.getKeyPrefix());
            }
            storageById.put(credential.getId(), credential);
            storageByPrefix.put(credential.getKeyPrefix(), credential);
        }

        @Override
        public Optional<ApiCredential> findById(UUID id) {
            return Optional.ofNullable(storageById.get(id));
        }

        @Override
        public Optional<ApiCredential> findByKeyPrefix(String keyPrefix) {
            return Optional.ofNullable(storageByPrefix.get(keyPrefix));
        }
    }

    private static class SequenceApiKeyGenerator implements ApiKeyGeneratorPort {
        int callCount = 0;

        @Override
        public GeneratedApiKey generateTestKey() {
            callCount++;
            String prefix = String.format("prefixSeq%03d", callCount);
            String secret = String.format("secret%03d", callCount);
            return new GeneratedApiKey(prefix, "pg_test_" + prefix + "_" + secret);
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
            throw new UnsupportedOperationException("verify is not used in CreateApiCredentialUseCase");
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
