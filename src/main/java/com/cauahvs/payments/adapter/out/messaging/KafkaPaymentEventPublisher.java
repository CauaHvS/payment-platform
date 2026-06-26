package com.cauahvs.payments.adapter.out.messaging;

import com.cauahvs.payments.application.port.out.PaymentEventPublisher;
import com.cauahvs.payments.domain.Payment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    public static final String TOPIC = "payment.created";

    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishPaymentCreated(Payment payment) {
        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);
        kafkaTemplate.send(TOPIC, payment.id().toString(), event);
    }
}