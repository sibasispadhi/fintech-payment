package com.example.payment.service;

import com.example.payment.model.PaymentRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A deliberately simple payment authorization simulator for demonstrating:
 * - OpenTelemetry tracing (span hierarchy: payment_authorize -> fraud_check/partner_auth/db_persist)
 * - Prometheus metrics (latency histogram + counters)
 *
 * Use payment.partnerDelayMs (or env var PAYMENT_PARTNER_DELAY_MS) to create "before/after" scenarios.
 */
@Service
public class PaymentService {

    private static final Tracer tracer =
            GlobalOpenTelemetry.getTracer("fintech.payment");

    private final Timer paymentLatency;
    private final Counter approved;
    private final Counter failed;

    @Value("${payment.partnerDelayMs:300}")
    private long partnerDelayMs;

    public PaymentService(MeterRegistry registry) {
        this.paymentLatency = Timer.builder("payment_latency")
                .description("End-to-end latency of payment authorization")
                .publishPercentileHistogram()
                .register(registry);

        this.approved = Counter.builder("payment_approved_total")
                .description("Total approved payments")
                .register(registry);

        this.failed = Counter.builder("payment_failed_total")
                .description("Total failed payments")
                .register(registry);
    }

    public boolean authorize(PaymentRequest req) {
        return paymentLatency.record(() -> doAuthorize(req));
    }

    private boolean doAuthorize(PaymentRequest req) {
        Span root = tracer.spanBuilder("payment_authorize")
                .setAttribute("user.id", safe(req.userId()))
                .setAttribute("currency", safe(req.currency()))
                .setAttribute("payment.method", safe(req.paymentMethod()))
                .setAttribute("amount", req.amount() != null ? req.amount().doubleValue() : 0.0)
                .startSpan();

        try (Scope scope = root.makeCurrent()) {
            fraudCheck();
            partnerAuth();
            dbPersist();

            boolean ok = ThreadLocalRandom.current().nextInt(100) >= 10; // 90% success
            if (ok) {
                approved.increment();
                root.setStatus(StatusCode.OK);
            } else {
                failed.increment();
                root.setStatus(StatusCode.ERROR, "simulated decline");
            }
            return ok;

        } catch (Exception e) {
            failed.increment();
            root.recordException(e);
            root.setStatus(StatusCode.ERROR, "exception");
            throw e;

        } finally {
            root.end();
        }
    }

    private void fraudCheck() throws InterruptedException {
        Span span = tracer.spanBuilder("fraud_check").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(randBetween(15, 30));
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    private void partnerAuth() throws InterruptedException {
        Span span = tracer.spanBuilder("partner_auth")
                .setAttribute("partner.delay_ms", partnerDelayMs)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(Math.max(0, partnerDelayMs));
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    private void dbPersist() throws InterruptedException {
        Span span = tracer.spanBuilder("db_persist").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Thread.sleep(randBetween(10, 20));
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    private static long randBetween(int minMs, int maxMs) {
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1L);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
