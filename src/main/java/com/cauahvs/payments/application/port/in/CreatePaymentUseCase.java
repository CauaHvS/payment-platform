package com.cauahvs.payments.application.port.in;

import com.cauahvs.payments.domain.Payment;

import java.math.BigDecimal;

public interface CreatePaymentUseCase {

    Payment execute(CreatePaymentCommand command);

    record CreatePaymentCommand(
            String payerId,
            String payeeId,
            BigDecimal amount,
            String currency
    ){}
}
