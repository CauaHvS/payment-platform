package com.cauahvs.payments.integration;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@DisplayName("Fluxo completo de pagamento via Kafka")
class PaymentFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("deve processar pagamento de ponta a ponta: PENDING -> COMPLETED/FAILED")
    void deveProcessarPagamentoDePontaAPonta() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                "payer-001", "payee-002",
                new BigDecimal("100.00"), "BRL", "test-user");

        Payment created = createPaymentUseCase.execute(command);
        UUID paymentId = created.id();

        assertThat(created.status()).isEqualTo(PaymentStatus.PENDING);

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment processed = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(processed.status()).isIn(
                            PaymentStatus.COMPLETED, PaymentStatus.FAILED);
                });
    }
}