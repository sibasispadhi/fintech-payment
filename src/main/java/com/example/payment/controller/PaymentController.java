package com.example.payment.controller;

import com.example.payment.model.PaymentRequest;
import com.example.payment.model.PaymentResponse;
import com.example.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(@RequestBody PaymentRequest req) {
        long start = System.currentTimeMillis();
        boolean ok = service.authorize(req);
        long latencyMs = System.currentTimeMillis() - start;

        if (ok) {
            return ResponseEntity.ok(new PaymentResponse(true, "authorized", latencyMs));
        }
        return ResponseEntity.badRequest().body(new PaymentResponse(false, "declined", latencyMs));
    }
}
