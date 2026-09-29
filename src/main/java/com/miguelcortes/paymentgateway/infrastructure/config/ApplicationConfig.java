package com.miguelcortes.paymentgateway.infrastructure.config;

import com.miguelcortes.paymentgateway.application.port.out.IdGenerator;
import com.miguelcortes.paymentgateway.application.port.out.PaymentRepositoryPort;
import com.miguelcortes.paymentgateway.application.port.out.TimeProvider;
import com.miguelcortes.paymentgateway.application.usecase.CreatePaymentUseCase;
import com.miguelcortes.paymentgateway.application.usecase.GetPaymentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    public CreatePaymentUseCase createPaymentUseCase(
            PaymentRepositoryPort paymentRepositoryPort,
            IdGenerator idGenerator,
            TimeProvider timeProvider
    ) {
        return new CreatePaymentUseCase(paymentRepositoryPort, idGenerator, timeProvider);
    }

    @Bean
    public GetPaymentUseCase getPaymentUseCase(PaymentRepositoryPort paymentRepositoryPort) {
        return new GetPaymentUseCase(paymentRepositoryPort);
    }
}
