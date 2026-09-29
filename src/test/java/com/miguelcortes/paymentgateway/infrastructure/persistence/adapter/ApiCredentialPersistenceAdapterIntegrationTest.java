package com.miguelcortes.paymentgateway.infrastructure.persistence.adapter;

import com.miguelcortes.paymentgateway.application.exception.DuplicateKeyPrefixException;
import com.miguelcortes.paymentgateway.domain.model.ApiCredential;
import com.miguelcortes.paymentgateway.domain.model.CredentialStatus;
import com.miguelcortes.paymentgateway.domain.model.Merchant;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.ApiCredentialMapper;
import com.miguelcortes.paymentgateway.infrastructure.persistence.mapper.MerchantMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ApiCredentialPersistenceAdapter.class,
        ApiCredentialMapper.class,
        MerchantPersistenceAdapter.class,
        MerchantMapper.class
})
@Testcontainers
class ApiCredentialPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ApiCredentialPersistenceAdapter credentialAdapter;

    @Autowired
    private MerchantPersistenceAdapter merchantAdapter;

    private static final String HASH_1 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String HASH_2 = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";

    @Test
    @DisplayName("Should save and retrieve ApiCredential by id and by keyPrefix")
    void shouldSaveAndRetrieveApiCredentialByIdAndPrefix() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Cred Merchant 1", "cred1@test.com", Instant.now()));

        UUID credentialId = UUID.randomUUID();
        String prefix = "prefTest0001";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        ApiCredential credential = new ApiCredential(
                credentialId,
                merchantId,
                prefix,
                HASH_1,
                createdAt
        );

        credentialAdapter.save(credential);

        Optional<ApiCredential> byId = credentialAdapter.findById(credentialId);
        assertTrue(byId.isPresent());
        ApiCredential savedById = byId.get();
        assertEquals(credentialId, savedById.getId());
        assertEquals(merchantId, savedById.getMerchantId());
        assertEquals(prefix, savedById.getKeyPrefix());
        assertEquals(HASH_1, savedById.getKeyHash());
        assertEquals(CredentialStatus.ACTIVE, savedById.getStatus());
        assertEquals(createdAt, savedById.getCreatedAt());
        assertNull(savedById.getRevokedAt());

        Optional<ApiCredential> byPrefix = credentialAdapter.findByKeyPrefix(prefix);
        assertTrue(byPrefix.isPresent());
        assertEquals(credentialId, byPrefix.get().getId());
    }

    @Test
    @DisplayName("Should translate unique key_prefix constraint violation to DuplicateKeyPrefixException")
    void shouldTranslateDuplicateKeyPrefixConstraint() {
        UUID merchantId = UUID.randomUUID();
        merchantAdapter.save(new Merchant(merchantId, "Cred Merchant 2", "cred2@test.com", Instant.now()));

        String duplicatePrefix = "prefDuplicate";

        ApiCredential cred1 = new ApiCredential(
                UUID.randomUUID(),
                merchantId,
                duplicatePrefix,
                HASH_1,
                Instant.now()
        );
        credentialAdapter.save(cred1);

        ApiCredential cred2 = new ApiCredential(
                UUID.randomUUID(),
                merchantId,
                duplicatePrefix,
                HASH_2,
                Instant.now()
        );

        DuplicateKeyPrefixException ex = assertThrows(
                DuplicateKeyPrefixException.class,
                () -> credentialAdapter.save(cred2)
        );

        assertTrue(ex.getMessage().contains(duplicatePrefix));
    }

    @Test
    @DisplayName("Should throw DataIntegrityViolationException on invalid foreign key and NOT translate to DuplicateKeyPrefixException")
    void shouldPropagateDataIntegrityViolationOnInvalidMerchantForeignKey() {
        UUID nonExistentMerchantId = UUID.randomUUID();

        ApiCredential orphanCredential = new ApiCredential(
                UUID.randomUUID(),
                nonExistentMerchantId,
                "orphanPrefix1",
                HASH_1,
                Instant.now()
        );

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> credentialAdapter.save(orphanCredential)
        );

        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("fk_api_credentials_merchants")
                || (ex.getCause() != null && ex.getCause().getMessage().contains("fk_api_credentials_merchants")));
    }

    @Test
    @DisplayName("Should return empty Optional when searching for non-existent prefix")
    void shouldReturnEmptyForNonExistentPrefix() {
        Optional<ApiCredential> result = credentialAdapter.findByKeyPrefix("nonExistentPrf");
        assertTrue(result.isEmpty());
    }
}
