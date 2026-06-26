package com.cauahvs.payments.application.service;

import com.cauahvs.payments.application.port.in.ProcessPaymentUseCase;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import com.cauahvs.payments.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessPaymentService.class);

    private final PaymentRepository paymentRepository;

    public ProcessPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public void process(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.isPending()) {
            log.info("Payment {} already processed (status={}), skipping", paymentId, payment.status());
            return;
        }

        payment.startProcessing();
        paymentRepository.save(payment);
        log.info("Payment {} started processing", paymentId);

        boolean approved = simulateGatewayCall();

        if (approved) {
            payment.complete();
            log.info("Payment {} completed", paymentId);
        } else {
            payment.fail();
            log.info("Payment {} failed", paymentId);
        }
        paymentRepository.save(payment);
    }

    private boolean simulateGatewayCall() {
        try {
            Thread.sleep(500); // simula latência de gateway externo
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ThreadLocalRandom.current().nextInt(100) < 80; // 80% aprovação
    }
}