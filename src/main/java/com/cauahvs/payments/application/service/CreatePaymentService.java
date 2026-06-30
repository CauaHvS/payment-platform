package com.cauahvs.payments.application.service;

import com.cauahvs.payments.application.port.in.CreatePaymentUseCase;
import com.cauahvs.payments.application.port.out.PaymentEventPublisher;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Currency;
import com.cauahvs.payments.domain.Money;
import com.cauahvs.payments.domain.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreatePaymentService implements CreatePaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    public CreatePaymentService(PaymentRepository paymentRepository,
                                PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Payment execute(CreatePaymentCommand command) {
        Currency currency = Currency.valueOf(command.currency());
        Money money = new Money(command.amount(), currency);

        Payment payment = Payment.create(UUID.randomUUID(),
                command.payerId(), command.payeeId(), money, command.createdBy());

        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishPaymentCreated(saved);
        return saved;
    }
}