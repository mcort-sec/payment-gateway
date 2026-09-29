package com.miguelcortes.paymentgateway.domain.model;

import com.miguelcortes.paymentgateway.domain.exception.InvalidMerchantException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class Merchant {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UUID id;
    private final String name;
    private final String email;
    private MerchantStatus status;
    private final Instant createdAt;

    public Merchant(UUID id, String name, String email, Instant createdAt) {
        this(id, name, email, MerchantStatus.ACTIVE, createdAt);
    }

    public Merchant(UUID id, String name, String email, MerchantStatus status, Instant createdAt) {
        validateId(id);
        validateName(name);
        validateEmail(email);
        validateStatus(status);
        validateCreatedAt(createdAt);

        this.id = id;
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Merchant reconstitute(
            UUID id,
            String name,
            String email,
            MerchantStatus status,
            Instant createdAt
    ) {
        return new Merchant(id, name, email, status, createdAt);
    }

    public void suspend() {
        this.status = MerchantStatus.SUSPENDED;
    }

    public void activate() {
        this.status = MerchantStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == MerchantStatus.ACTIVE;
    }

    private void validateId(UUID id) {
        if (id == null) {
            throw new InvalidMerchantException("Merchant ID cannot be null");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidMerchantException("Merchant name cannot be null, empty, or blank");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidMerchantException("Merchant email cannot be null, empty, or blank");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidMerchantException("Merchant email format is invalid: " + email);
        }
    }

    private void validateStatus(MerchantStatus status) {
        if (status == null) {
            throw new InvalidMerchantException("Merchant status cannot be null");
        }
    }

    private void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidMerchantException("Creation timestamp cannot be null");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Merchant merchant = (Merchant) o;
        return Objects.equals(id, merchant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
