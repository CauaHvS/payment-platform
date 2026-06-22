package com.cauahvs.payments.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, Currency currency) {
        public Money {
        if (amount == null){
            throw new IllegalArgumentException("amount must not be null.");
        }

        if (currency == null){
            throw new IllegalArgumentException("currency must not be null.");
        }

        if (amount.signum() < 0){
            throw new IllegalArgumentException("amount must not be negative.");
        }

    }
}
