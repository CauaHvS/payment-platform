package com.cauahvs.payments.integration;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.port.out.PaymentEventPublisher;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Idempotencia do processamento de pagamento")
class PaymentIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentEventPublisher eventPublisher;

    @Test
    @DisplayName("reprocessar o mesmo evento nao altera o pagamento ja finalizado")
    void deveSerIdempotente_aoReprocessarMesmoEvento() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                "payer-001", "payee-002",
                new BigDecimal("100.00"), "BRL", "test-user");
        UUID paymentId = createPaymentUseCase.execute(command).id();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment p = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(p.status()).isIn(PaymentStatus.COMPLETED, PaymentStatus.FAILED);
                });

        Payment afterFirst = paymentRepository.findById(paymentId).orElseThrow();
        PaymentStatus statusAfterFirst = afterFirst.status();
        Instant updatedAtAfterFirst = afterFirst.updatedAt();

        eventPublisher.publishPaymentCreated(afterFirst);

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(4))
                .untilAsserted(() -> {
                    Payment p = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(p.status()).isEqualTo(statusAfterFirst);
                    assertThat(p.updatedAt()).isEqualTo(updatedAtAfterFirst);
                });
    }
}