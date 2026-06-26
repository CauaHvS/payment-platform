package com.cauahvs.payments.adapter.out.persistence;

import com.cauahvs.payments.TestcontainersConfiguration;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Currency;
import com.cauahvs.payments.domain.Money;
import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("PaymentRepositoryAdapter — testes de integração com Postgres real")
class PaymentRepositoryAdapterIT {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void devePersistirERecuperarPaymentDoPostgres() {
        // ARRANGE
        Payment original = Payment.create(
                UUID.randomUUID(),
                "payer-001",
                "payee-002",
                new Money(new BigDecimal("150.75"), Currency.BRL)
        );

        // ACT
        paymentRepository.save(original);
        Optional<Payment> loaded = paymentRepository.findById(original.id());

        // ASSERT
        assertThat(loaded).isPresent();
        Payment recovered = loaded.get();
        assertThat(recovered.id()).isEqualTo(original.id());
        assertThat(recovered.payerId()).isEqualTo("payer-001");
        assertThat(recovered.payeeId()).isEqualTo("payee-002");
        assertThat(recovered.money().amount()).isEqualByComparingTo(new BigDecimal("150.75"));
        assertThat(recovered.money().currency()).isEqualTo(Currency.BRL);
        assertThat(recovered.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(recovered.createdAt()).isNotNull();
        assertThat(recovered.updatedAt()).isNotNull();
    }

    @Test
    void deveRetornarOptionalEmpty_quandoPaymentNaoExiste() {
        Optional<Payment> result = paymentRepository.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}