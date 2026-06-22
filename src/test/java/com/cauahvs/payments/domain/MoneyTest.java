package com.cauahvs.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void deveCriarMoneyValido_quandoAmountECurrencySaoValidos(){
        BigDecimal amount = new BigDecimal("10.00");
        Currency currency = Currency.BRL;

        Money money = new Money(amount, currency);

        assertThat(money.amount()).isEqualTo(amount);
        assertThat(money.currency()).isEqualTo(currency);
    }

    @Test
    void deveLancarExcecao_quandoAmountEhNull(){
        BigDecimal amount = null;
        Currency currency = Currency.BRL;

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must not be null");
    }

    @Test
    void deveLancarExcecao_quandoCurrencyEhNull(){
        BigDecimal amount = new BigDecimal("10.00");
        Currency currency = null;

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency must not be null");
    }

    @Test
    void deveLancarExcecao_quandoAmountEhNegativo(){
        BigDecimal amount = new BigDecimal("-10.00");
        Currency currency = Currency.BRL;

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must not be negative");
    }
}
