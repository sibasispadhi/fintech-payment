# fintech-payment

A small, runnable Spring Boot payment microservice demo intended for CNCF-aligned content:
- Kubernetes deployment (minikube/kind)
- OpenTelemetry tracing (logs by default; OTLP exporter optional)
- Prometheus metrics via Micrometer (`/actuator/prometheus`)
- Simple autoscaling example (CPU-based HPA)

## Quickstart (local JVM)

```bash
mvn -DskipTests package
java -jar target/fintech-payment-0.1.0.jar
```

Test:

```bash
curl -s -X POST http://localhost:8080/payments/authorize \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","amount":10.00,"currency":"USD","paymentMethod":"card"}'
```

Metrics:

- http://localhost:8080/actuator/prometheus
- health: http://localhost:8080/actuator/health

## Before/after knob

Change partner delay to simulate slower/faster downstream dependency:

- `payment.partnerDelayMs` in `src/main/resources/application.yml`
- or env var: `PAYMENT_PARTNER_DELAY_MS`

Example:

```bash
PAYMENT_PARTNER_DELAY_MS=900 java -jar target/fintech-payment-0.1.0.jar
```

## OpenTelemetry

By default, spans are exported to application logs (LoggingSpanExporter).

To export to an OpenTelemetry Collector via OTLP gRPC:

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_SERVICE_NAME=fintech-payment
java -jar target/fintech-payment-0.1.0.jar
```

## Kubernetes (minikube)

Build and load image into minikube:

```bash
minikube start
eval $(minikube -p minikube docker-env)
docker build -t fintech-payment:local .
```

Deploy:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa-cpu.yaml
```

Port-forward:

```bash
kubectl -n fintech port-forward svc/payment-service 8080:8080
```

Load test (example with hey):

```bash
hey -z 3m -c 200 -m POST \
  -H "Content-Type: application/json" \
  -d '{"userId":"u1","amount":10.00,"currency":"USD","paymentMethod":"card"}' \
  http://localhost:8080/payments/authorize
```

## PromQL snippets

P95 latency:

```promql
histogram_quantile(0.95, sum(rate(payment_latency_seconds_bucket[5m])) by (le))
```

Error rate:

```promql
sum(rate(payment_failed_total[5m])) / sum(rate(payment_failed_total[5m]) + rate(payment_approved_total[5m]))
```
