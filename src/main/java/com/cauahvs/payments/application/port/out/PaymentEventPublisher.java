package com.cauahvs.payments.application.port.out;

import com.cauahvs.payments.domain.Payment;

public interface PaymentEventPublisher {
    void publishPaymentCreated(Payment payment);
}
