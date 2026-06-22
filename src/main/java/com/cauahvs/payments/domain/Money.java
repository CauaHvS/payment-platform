package com.cauahvs.payments.domain;

import java.math.BigDecimal;
import java.util.Objects;

public class Money {
    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null){
            throw new IllegalArgumentException("amount must not be null.");
        }

        if (currency == null || currency.isBlank()){
            throw new IllegalArgumentException("currency must not be blank.");
        }

        if (amount.signum() < 0){
            throw new IllegalArgumentException("amount must not be negative.");
        }
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return "Money{" +
                "amount=" + amount +
                ", currency='" + currency + '\'' +
                '}';
    }

    public BigDecimal getAmount(){return amount;}
    public String getCurrency(){return currency;}
}
