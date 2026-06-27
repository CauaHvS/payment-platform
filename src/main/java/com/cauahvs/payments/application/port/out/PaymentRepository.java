package com.cauahvs.payments.application.port.out;

import com.cauahvs.payments.domain.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    List<Payment> findAll();
}