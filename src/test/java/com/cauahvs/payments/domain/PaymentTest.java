package com.cauahvs.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void deveCriarPaymentValido_quandoTodosOsCamposSaoValidos(){
        UUID id = UUID.randomUUID();
        String payerId = "payer-123";
        String payeeId = "payee-456";
        Money money = new Money(new BigDecimal("100.00"), Currency.BRL);

        Payment payment = Payment.create(id, payerId, payeeId, money);

        assertThat(payment.id()).isEqualTo(id);
        assertThat(payment.payerId()).isEqualTo(payerId);
        assertThat(payment.payeeId()).isEqualTo(payeeId);
        assertThat(payment.money()).isEqualTo(money);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.createdAt()).isNotNull();
        assertThat(payment.updatedAt()).isEqualTo(payment.createdAt());
    }

    @Test
    void deveLancarExcecao_quandoIdEhNull(){
        Money money = new Money(new BigDecimal("100.00"), Currency.BRL);

        assertThatThrownBy(() -> Payment.create(null, "payer-123", "payee-456", money))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void deveLancarExcecao_quandoPayerIdEhBlank(){
        Money money = new Money(new BigDecimal("100.00"), Currency.BRL);

        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), "", "payee-456", money))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payerId must not be blank");
    }

    @Test
    void deveLancarExcecao_quandoPayeeIdEhBlank (){
        Money money = new Money(new BigDecimal("100.00"), Currency.BRL);

        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), "payer-123", "", money))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payeeId must not be blank");
    }

    @Test
    void deveLancarExcecao_quandoMoneyEhNull(){
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), "payer-123", "payee-456", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("money must not be null");
    }

    @Test
    void deveTransicionarParaProcessing_quandoStartProcessingEhChamadoEmPending(){
        Payment payment = createValidPayment();
        // payment nasce em PENDING

        payment.startProcessing();

        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.isProcessing()).isTrue();
    }

    @Test
    void deveTransicionarParaCompleted_quandoCompleteEhChamadoEmProcessing(){
        Payment payment = createValidPayment();

        payment.startProcessing();
        payment.complete();

        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.isCompleted()).isTrue();
    }

    @Test
    void deveTransicionarParaFailed_quandoFailEhChamadoEmProcessing(){
        Payment payment = createValidPayment();

        payment.startProcessing();
        payment.fail();

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.isFailed()).isTrue();
    }

    @Test
    void deveLancarExcecao_quandoStartProcessingEhChamadoEmProcessing(){
        Payment payment = createValidPayment();
        payment.startProcessing();

        assertThatThrownBy(payment::startProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be PENDING");
    }

    @Test
    void deveLancarExcecao_quandoStartProcessingEhChamadoEmCompleted(){
        Payment payment = createValidPayment();

        payment.startProcessing();
        payment.complete();

        assertThatThrownBy(payment::startProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be PENDING");
    }

    @Test
    void deveLancarExcecao_quandoCompleteEhChamadoEmPending(){
        Payment payment = createValidPayment();

        assertThatThrownBy(payment::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be PROCESSING");
    }

    @Test
    void deveLancarExcecao_quandoFailEhChamadoEmPending(){
        Payment payment = createValidPayment();

        assertThatThrownBy(payment::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be PROCESSING");
    }

    @Test
    void deveLancarExcecao_quandoCompleteEhChamadoEmFailed (){
        Payment payment = createValidPayment();

        payment.startProcessing();
        payment.fail();

        assertThatThrownBy(payment::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be PROCESSING");
    }

    @Test
    void deveAtualizarUpdatedAt_quandoTransicaoOcorre() throws InterruptedException{
        Payment payment = createValidPayment();
        Instant before = payment.updatedAt();

        Thread.sleep(10);

        payment.startProcessing();

        assertThat(payment.updatedAt()).isAfter(before);
    }

    private Payment createValidPayment() {
        return Payment.create(
                UUID.randomUUID(),
                "payer-123",
                "payee-456",
                new Money(new BigDecimal("100.00"), Currency.BRL)
        );
    }
}
