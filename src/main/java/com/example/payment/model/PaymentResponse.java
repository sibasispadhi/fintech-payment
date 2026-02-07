package com.example.payment.model;

public record PaymentResponse(
        boolean approved,
        String message,
        long latencyMs
) {}
