package com.example.payment.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;

/**
 * Minimal OpenTelemetry SDK bootstrap.
 *
 * Behavior:
 * - If OTEL_EXPORTER_OTLP_ENDPOINT is set, exports spans via OTLP gRPC to that endpoint.
 * - Otherwise exports spans to application logs (LoggingSpanExporter).
 *
 * This keeps the project "works out of the box" while still supporting real tracing backends.
 */
@Configuration
public class OpenTelemetryConfig implements DisposableBean {

    private SdkTracerProvider tracerProvider;

    @Bean
    public OpenTelemetry openTelemetry() {
        String serviceName = System.getenv().getOrDefault("OTEL_SERVICE_NAME", "fintech-payment");
        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(SERVICE_NAME, serviceName))
        );

        String otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");

        SdkTracerProvider.Builder providerBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        if (otlpEndpoint != null && !otlpEndpoint.isBlank()) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .setTimeout(Duration.ofSeconds(5))
                    .build();
            providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
        } else {
            providerBuilder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
        }

        tracerProvider = providerBuilder.build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        GlobalOpenTelemetry.set(sdk);
        return sdk;
    }

    @Override
    public void destroy() {
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }
}
