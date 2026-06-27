package com.cauahvs.payments.integration;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Fluxo completo de pagamento via Kafka")
class PaymentFlowIT extends AbstractIntegrationTest {

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

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    Payment processed = paymentRepository.findById(paymentId).orElseThrow();
                    assertThat(processed.status()).isIn(
                            PaymentStatus.COMPLETED, PaymentStatus.FAILED);
                });
    }
}