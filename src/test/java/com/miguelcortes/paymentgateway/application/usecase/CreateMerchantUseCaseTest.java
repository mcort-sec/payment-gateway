package com.miguelcortes.paymentgateway.application.usecase;

import com.miguelcortes.paymentgateway.application.command.CreateMerchantCommand;
import com.miguelcortes.paymentgateway.application.exception.DuplicateMerchantEmailException;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.domain.model.MerchantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateMerchantUseCaseTest {

    private InMemoryMerchantRepository fakeRepository;
    private FakeIdGenerator fakeIdGenerator;
    private FakeTimeProvider fakeTimeProvider;
    private CreateMerchantUseCase useCase;

    private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED_TIME = Instant.parse("2026-09-28T10:00:00Z");

    @BeforeEach
    void setUp() {
        fakeRepository = new InMemoryMerchantRepository();
        fakeIdGenerator = new FakeIdGenerator(FIXED_ID);
        fakeTimeProvider = new FakeTimeProvider(FIXED_TIME);
        useCase = new CreateMerchantUseCase(fakeRepository, fakeIdGenerator, fakeTimeProvider);
    }

    @Test
    @DisplayName("Should create and save new active merchant when email is not taken")
    void shouldCreateAndSaveNewMerchantSuccessfully() {
        CreateMerchantCommand command = new CreateMerchantCommand("Acme Corp", "info@acme.com");

        Merchant createdMerchant = useCase.execute(command);

        assertEquals(FIXED_ID, createdMerchant.getId());
        assertEquals("Acme Corp", createdMerchant.getName());
        assertEquals("info@acme.com", createdMerchant.getEmail());
        assertEquals(MerchantStatus.ACTIVE, createdMerchant.getStatus());
        assertTrue(createdMerchant.isActive());
        assertEquals(FIXED_TIME, createdMerchant.getCreatedAt());

        assertEquals(1, fakeRepository.saveCallCount);
        assertSame(createdMerchant, fakeRepository.lastSavedMerchant);
        assertEquals(1, fakeIdGenerator.callCount);
        assertEquals(1, fakeTimeProvider.callCount);
    }

    @Test
    @DisplayName("Should throw DuplicateMerchantEmailException when email already exists")
    void shouldThrowDuplicateMerchantEmailExceptionWhenEmailAlreadyExists() {
        Merchant existingMerchant = new Merchant(
                UUID.randomUUID(),
                "Existing Store",
                "info@acme.com",
                Instant.now()
        );
        fakeRepository.save(existingMerchant);
        fakeRepository.saveCallCount = 0;

        CreateMerchantCommand command = new CreateMerchantCommand("Duplicate Store", "INFO@ACME.COM");

        DuplicateMerchantEmailException exception = assertThrows(
                DuplicateMerchantEmailException.class,
                () -> useCase.execute(command)
        );

        assertTrue(exception.getMessage().contains("info@acme.com"));
        assertEquals(0, fakeRepository.saveCallCount);
        assertEquals(0, fakeIdGenerator.callCount);
        assertEquals(0, fakeTimeProvider.callCount);
    }

    // --- Fakes Manuales ---

    private static class InMemoryMerchantRepository implements MerchantRepositoryPort {
        private final Map<UUID, Merchant> storageById = new HashMap<>();
        private final Map<String, Merchant> storageByEmail = new HashMap<>();
        int saveCallCount = 0;
        Merchant lastSavedMerchant;

        @Override
        public void save(Merchant merchant) {
            saveCallCount++;
            lastSavedMerchant = merchant;
            storageById.put(merchant.getId(), merchant);
            storageByEmail.put(merchant.getEmail(), merchant);
        }

        @Override
        public Optional<Merchant> findById(UUID id) {
            return Optional.ofNullable(storageById.get(id));
        }

        @Override
        public Optional<Merchant> findByEmail(String email) {
            return Optional.ofNullable(storageByEmail.get(email));
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
