package com.cauahvs.payments.adapter.out.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID paymentId,
        String payerId,
        String payeeId,
        BigDecimal amount,
        String currency,
        String createdBy,
        Instant occurredAt
) {
    public static PaymentCreatedEvent from(com.cauahvs.payments.domain.Payment payment) {
        return new PaymentCreatedEvent(
                payment.id(),
                payment.payerId(),
                payment.payeeId(),
                payment.money().amount(),
                payment.money().currency().name(),
                payment.createdBy(),
                Instant.now()
        );
    }
}