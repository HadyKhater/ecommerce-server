# syntax=docker/dockerfile:1
#
# Shared multi-stage Dockerfile for every Spring Boot module.
# The dependency/build stage is shared by all targets, so Docker builds it once
# and reuses the cache for the remaining services.
#
# Build a single service with:
#   docker build --target auth-service -t ecommerce-auth-service .
#
# NOTE: this project is Java 21 / Spring Boot 3.5 (see pom.xml), so the build
# image uses Temurin 21 rather than 17. All base images are multi-arch (amd64/arm64).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) Copy only POMs first so Maven dependency downloads stay cached.
COPY pom.xml ./
COPY eureka-server/pom.xml eureka-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY cart-service/pom.xml cart-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
RUN mvn -B -q dependency:go-offline -DskipTests || true

# 2) Copy sources and build all modules.
COPY eureka-server/src eureka-server/src
COPY api-gateway/src api-gateway/src
COPY auth-service/src auth-service/src
COPY product-service/src product-service/src
COPY cart-service/src cart-service/src
COPY inventory-service/src inventory-service/src
COPY order-service/src order-service/src
COPY payment-service/src payment-service/src
COPY notification-service/src notification-service/src
RUN mvn -B -q -DskipTests package

# 3) Small, non-root runtime base used by every service.
FROM eclipse-temurin:21-jre AS runtime-base
RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app \
    && chown -R appuser:appuser /app
# busybox gives us a tiny wget for Docker healthchecks (the JRE image has no curl).
COPY --from=busybox:1.36 /bin/busybox /usr/bin/busybox
WORKDIR /app
USER appuser
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Djava.security.egd=file:/dev/./urandom"

FROM runtime-base AS eureka-server
COPY --from=build --chown=appuser:appuser /workspace/eureka-server/target/eureka-server-*.jar /app/app.jar
EXPOSE 8761

FROM runtime-base AS api-gateway
COPY --from=build --chown=appuser:appuser /workspace/api-gateway/target/api-gateway-*.jar /app/app.jar
EXPOSE 8080

FROM runtime-base AS auth-service
COPY --from=build --chown=appuser:appuser /workspace/auth-service/target/auth-service-*.jar /app/app.jar
EXPOSE 8081

FROM runtime-base AS product-service
COPY --from=build --chown=appuser:appuser /workspace/product-service/target/product-service-*.jar /app/app.jar
EXPOSE 8082

FROM runtime-base AS inventory-service
COPY --from=build --chown=appuser:appuser /workspace/inventory-service/target/inventory-service-*.jar /app/app.jar
EXPOSE 8083

FROM runtime-base AS order-service
COPY --from=build --chown=appuser:appuser /workspace/order-service/target/order-service-*.jar /app/app.jar
EXPOSE 8084

FROM runtime-base AS payment-service
COPY --from=build --chown=appuser:appuser /workspace/payment-service/target/payment-service-*.jar /app/app.jar
EXPOSE 8085

FROM runtime-base AS notification-service
COPY --from=build --chown=appuser:appuser /workspace/notification-service/target/notification-service-*.jar /app/app.jar
EXPOSE 8086

FROM runtime-base AS cart-service
COPY --from=build --chown=appuser:appuser /workspace/cart-service/target/cart-service-*.jar /app/app.jar
EXPOSE 8087

# Every runtime stage starts the same jar.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
