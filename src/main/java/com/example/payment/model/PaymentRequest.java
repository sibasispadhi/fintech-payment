package com.example.payment.model;

import java.math.BigDecimal;

public record PaymentRequest(
        String userId,
        BigDecimal amount,
        String currency,
        String paymentMethod
) {}
