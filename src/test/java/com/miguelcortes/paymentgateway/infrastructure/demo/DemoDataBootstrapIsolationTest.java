package com.miguelcortes.paymentgateway.infrastructure.demo;

import com.miguelcortes.paymentgateway.application.port.out.MerchantRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class DemoDataBootstrapIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MerchantRepositoryPort merchantRepository;

    @Test
    @DisplayName("Should not register DemoDataBootstrap bean in default profile")
    void shouldNotLoadDemoDataBootstrapInDefaultProfile() {
        assertEquals(0, applicationContext.getBeansOfType(DemoDataBootstrap.class).size());
        assertTrue(merchantRepository.findById(DemoDataBootstrap.ACTIVE_MERCHANT_ID).isEmpty());
        assertTrue(merchantRepository.findById(DemoDataBootstrap.SUSPENDED_MERCHANT_ID).isEmpty());
    }
}
