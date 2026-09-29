package com.miguelcortes.paymentgateway.application.port.out;

import com.miguelcortes.paymentgateway.domain.model.Merchant;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepositoryPort {

    void save(Merchant merchant);

    Optional<Merchant> findById(UUID id);

    Optional<Merchant> findByEmail(String email);
}
