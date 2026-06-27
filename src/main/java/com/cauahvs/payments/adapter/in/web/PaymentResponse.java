package com.cauahvs.payments.adapter.in.web;

import com.cauahvs.payments.domain.Payment;
import com.cauahvs.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String payerId,
        String payeeId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse fromDomain(Payment payment){
        return new PaymentResponse(
                payment.id(),
                payment.payerId(),
                payment.payeeId(),
                payment.money().amount(),
                payment.money().currency().name(),
                payment.status(),
                payment.createdAt(),
                payment.updatedAt()
        );
    }

    public boolean isFinal() {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED;
    }

}
