package com.miguelcortes.paymentgateway.infrastructure.config;

import com.miguelcortes.paymentgateway.application.port.out.ApiCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyGeneratorPort;
import com.miguelcortes.paymentgateway.application.port.out.ApiKeyHasherPort;
import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorCredentialRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.ProcessorRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.RefundReservationPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.application.usecase.ApprovePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.ApproveRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CancelPaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateApiCredentialUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateMerchantUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateProcessorCredentialUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateProcessorUseCase;
import com.miguelcortes.paymentgateway.application.usecase.CreateRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclinePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.DeclineRefundUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetPaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetRefundUseCase;
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
    public ApprovePaymentUseCase approvePaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        return new ApprovePaymentUseCase(paymentRepositoryPort, processorRepositoryPort);
    }

    @Bean
    public DeclinePaymentUseCase declinePaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        return new DeclinePaymentUseCase(paymentRepositoryPort, processorRepositoryPort);
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

    @Bean
    public CreateProcessorUseCase createProcessorUseCase(
            ProcessorRepositoryPort processorRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreateProcessorUseCase(
                processorRepositoryPort,
                idGenerator,
                timeProvider
        );
    }

    @Bean
    public CreateProcessorCredentialUseCase createProcessorCredentialUseCase(
            ProcessorCredentialRepositoryPort processorCredentialRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort,
            ApiKeyGeneratorPort apiKeyGeneratorPort,
            ApiKeyHasherPort apiKeyHasherPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreateProcessorCredentialUseCase(
                processorCredentialRepositoryPort,
                processorRepositoryPort,
                apiKeyGeneratorPort,
                apiKeyHasherPort,
                idGenerator,
                timeProvider
        );
    }

    @Bean
    public CreateRefundUseCase createRefundUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            RefundRepositoryPort refundRepositoryPort,
            RefundReservationPort refundReservationPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreateRefundUseCase(
                paymentRepositoryPort,
                refundRepositoryPort,
                refundReservationPort,
                idGenerator,
                timeProvider
        );
    }

    @Bean
    public ApproveRefundUseCase approveRefundUseCase(
            RefundRepositoryPort refundRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        return new ApproveRefundUseCase(refundRepositoryPort, processorRepositoryPort);
    }

    @Bean
    public DeclineRefundUseCase declineRefundUseCase(
            RefundRepositoryPort refundRepositoryPort,
            ProcessorRepositoryPort processorRepositoryPort
    ) {
        return new DeclineRefundUseCase(refundRepositoryPort, processorRepositoryPort);
    }

    @Bean
    public GetRefundUseCase getRefundUseCase(RefundRepositoryPort refundRepositoryPort) {
        return new GetRefundUseCase(refundRepositoryPort);
    }
}
