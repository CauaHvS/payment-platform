package com.cauahvs.payments.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
        public Money {
        if (amount == null){
            throw new IllegalArgumentException("amount must not be null.");
        }

        if (currency == null || currency.isBlank()){
            throw new IllegalArgumentException("currency must not be blank.");
        }

        if (amount.signum() < 0){
            throw new IllegalArgumentException("amount must not be negative.");
        }

    }
}
