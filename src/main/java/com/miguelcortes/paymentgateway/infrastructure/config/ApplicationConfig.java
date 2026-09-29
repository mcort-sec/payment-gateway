package com.miguelcortes.paymentgateway.infrastructure.config;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.application.usecase.ApprovePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CancelPaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateApiCredentialUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateMerchantUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclinePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetPaymentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public CreatePaymentUseCase createPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            MerchantRepositoryPort merchantRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreatePaymentUseCase(paymentRepositoryPort, merchantRepositoryPort, idGenerator, timeProvider);
    }

    @Bean
    public GetPaymentUseCase getPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new GetPaymentUseCase(paymentRepositoryPort);
    }

    @Bean
    public ApprovePaymentUseCase approvePaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new ApprovePaymentUseCase(paymentRepositoryPort);
    }

    @Bean
    public DeclinePaymentUseCase declinePaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new DeclinePaymentUseCase(paymentRepositoryPort);
    }

    @Bean
    public CancelPaymentUseCase cancelPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new CancelPaymentUseCase(paymentRepositoryPort);
    }

    @Bean
    public CreateMerchantUseCase createMerchantUseCase(
            MerchantRepositoryPort merchantRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreateMerchantUseCase(
                merchantRepositoryPort,
                idGenerator,
                timeProvider
        );
    }

    @Bean
    public CreateApiCredentialUseCase createApiCredentialUseCase(
            ApiCredentialRepositoryPort apiCredentialRepositoryPort,
            MerchantRepositoryPort merchantRepositoryPort,
            ApiKeyGeneratorPort apiKeyGeneratorPort,
            ApiKeyHasherPort apiKeyHasherPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreateApiCredentialUseCase(
                apiCredentialRepositoryPort,
                merchantRepositoryPort,
                apiKeyGeneratorPort,
                apiKeyHasherPort,
                idGenerator,
                timeProvider
        );
    }
}
