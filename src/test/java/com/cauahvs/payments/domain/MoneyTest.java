package com.cauahvs.payments.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void deveCriarMoneyValido_quandoAmountECurrencySaoValidos(){
        BigDecimal amount = new BigDecimal("10.00");
        String currency = "BRL";

        Money money = new Money(amount, currency);

        assertThat(money.getAmount()).isEqualTo(amount);
        assertThat(money.getCurrency()).isEqualTo(currency);
    }

    @Test
    void deveLancarExcecao_quandoAmountEhNull(){
        BigDecimal amount = null;
        String currency = "BRL";

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must not be null");
    }

    @Test
    void deveLancarExcecao_quandoCurrencyEhNull(){
        BigDecimal amount = new BigDecimal("10.00");
        String currency = null;

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void deveLancarExcecao_quandoCurrencyEhVazia(){
        BigDecimal amount = new BigDecimal("10.00");
        String currency = "";

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void deveLancarExcecao_quandoCurrencyContemApenasEspacos(){
        BigDecimal amount = new BigDecimal("10.00");
        String currency = "   ";

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void deveLancarExcecao_quandoAmountEhNegativo(){
        BigDecimal amount = new BigDecimal("-10.00");
        String currency = "BRL";

        assertThatThrownBy(() -> new Money(amount, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must not be negative");
    }
}
