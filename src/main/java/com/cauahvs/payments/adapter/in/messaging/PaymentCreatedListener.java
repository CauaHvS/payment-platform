package com.cauahvs.payments.adapter.in.messaging;

import com.cauahvs.payments.adapter.out.messaging.PaymentCreatedEvent;
import com.cauahvs.payments.application.port.in.ProcessPaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile({"worker", "default"})
public class PaymentCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedListener.class);

    private final ProcessPaymentUseCase processPaymentUseCase;

    public PaymentCreatedListener(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @KafkaListener(topics = "payment.created", groupId = "payment-processor")
    public void onPaymentCreated(PaymentCreatedEvent event) {
        log.info("Received PaymentCreatedEvent for payment {} created by {}",
                event.paymentId(), event.createdBy());
        processPaymentUseCase.process(event.paymentId());
    }
}