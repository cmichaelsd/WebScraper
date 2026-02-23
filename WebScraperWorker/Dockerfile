# ---------- Build stage ----------
FROM gradle:8.7-jdk21 AS builder

WORKDIR /app
COPY . .

RUN gradle installDist

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY --from=builder /app/build/install/* /app/

ENV USE_SYSTEM_CA_CERTS=1

CMD ["./bin/WebScraper"]

