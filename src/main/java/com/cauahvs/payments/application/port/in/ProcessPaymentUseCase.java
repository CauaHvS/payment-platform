package com.cauahvs.payments.application.port.in;

import java.util.UUID;

public interface ProcessPaymentUseCase {
    void process(UUID paymentId);
}