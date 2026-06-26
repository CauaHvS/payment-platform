package com.cauahvs.payments.adapter.out.persistence.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID paymentId,
        String payerId,
        String payeeId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
    public static PaymentCreatedEvent from(com.cauahvs.payments.domain.Payment payment) {
        return new PaymentCreatedEvent(
                payment.id(),
                payment.payerId(),
                payment.payeeId(),
                payment.money().amount(),
                payment.money().currency().name(),
                Instant.now()
        );
    }
}
