package com.cauahvs.payments.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotBlank(message = "payerId is required.")
        String payerId,

        @NotBlank(message = "payeeId is required.")
        String payeeId,

        @NotNull(message = "amount is required.")
        @Positive(message = "amount must be positive.")
        BigDecimal amount,

        @NotBlank(message = "currency is required.")
        String currency
) {}
