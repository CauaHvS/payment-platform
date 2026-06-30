package com.cauahvs.payments.application.service;

import com.cauahvs.payments.adapter.in.web.payment.PaymentResponse;
import com.cauahvs.payments.application.port.out.PaymentRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetPaymentService {

    private final PaymentRepository paymentRepository;

    public GetPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Cacheable(value = "payments", key = "#id",
            unless = "#result == null || (#result.status().name() != 'COMPLETED' && #result.status().name() != 'FAILED')")
    public PaymentResponse findById(UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::fromDomain)
                .orElse(null);
    }

    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::fromDomain)
                .toList();
    }
}