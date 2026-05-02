# Constitution: High-Traffic E-Commerce Platform

## Overview

This document establishes the governing principles and architectural standards for building a high-traffic e-commerce platform. These principles guide all development decisions across the entire system.

**Target Deployment:** Local development environment  
**Scale:** High-traffic API-driven architecture  
**Last Updated:** April 2026

---

## Scope

- Microservices architecture using Spring Boot 3.5.14 (Java 21)
- Code quality standards and design patterns
- Security protocols and authentication mechanisms
- Data consistency and caching strategies
- Testing requirements and quality assurance standards

---

## Architecture Standards

### Service Design

- **Framework:** Microservices using Spring Boot 3.5.14 on Java 21
- **Deployment:** Each service runs on its own dedicated port locally
- **Communication:** Inter-service communication via OpenFeign REST calls
- **Discovery:** Spring Cloud Netflix Eureka for service discovery
- **Gateway:** Spring Cloud Gateway for API routing

### Technology Stack (No Heavy Infrastructure)

| Requirement | Solution | Rationale |
|---|---|---|
| Caching | Caffeine Cache | Spring Boot built-in, lightweight, no infrastructure |
| Message Queue | Spring ApplicationEventPublisher | In-process events, stateless notifications |
| Database | PostgreSQL | One database per service, local pgAdmin |
| Connection Pool | HikariCP | Efficient connection management |

---

## Code Quality Standards

### Controller Layer

- **Responsibility:** Input validation, request/response mapping only
- **Pattern:** Thin controllers, fat services
- **Validation:** Use `@Valid` annotations on all DTOs
- **Return Type:** Always explicit DTOs (not domain objects)

### Service Layer

- **Responsibility:** All business logic, validation, and rules
- **Interface:** Every public service method must have an interface (`IProductService`)
- **Dependency Injection:** Constructor injection only—never use `@Autowired` field injection
- **Exception Handling:** Throw specific, meaningful exceptions (never swallow or return null)

### Domain Models & DTOs

- **DTOs:** Use Java records where possible (`record ProductDTO(UUID id, String name)`)
- **IDs:** All entity IDs are UUIDs (no sequential integers)
- **Timestamps:** Use `java.time.Instant` (UTC only, never local time)
- **Magic Strings:** Forbidden—use enums and constants instead
- **Null Handling:** Never return null; use `Optional<T>` or throw specific exceptions

### Exception Handling

- **Global Handler:** `@ControllerAdvice` with RFC 7807 `ProblemDetail` format
- **Response Format:**
  ```json
  {
    "type": "https://api.example.com/errors/product-not-found",
    "title": "Product Not Found",
    "status": 404,
    "detail": "Product with ID 123e4567-e89b-12d3-a456-426614174000 not found"
  }
  ```

---

## Security Standards

### Authentication & Authorization

- **Algorithm:** JWT RS256 (asymmetric, RSA-2048)
- **Token Strategy:**
  - Access Token: 15 minutes
  - Refresh Token: 7 days
- **Password Hashing:** BCrypt with strength 12 (minimum)
- **Endpoints:** All admin endpoints protected with `@PreAuthorize("hasRole('ADMIN')")`

### Access Control

- **Roles:** USER (default) and ADMIN
- **JWT Validation:** Verify signature and expiration on all protected endpoints
- **Refresh Token Flow:** Implemented in auth-service

### Input Validation & Defense

- **SQL Injection Prevention:** Never concatenate strings in SQL queries; use JPA parameterized queries
- **Input Validation:** `@Valid` on all controller methods
- **XSS Protection:** Handled by Angular frontend (CSP headers)

---

## Data Consistency Model

### Strong Consistency (ACID)

**Applied to:** Inventory, Orders, Payments

- **Implementation:** Optimistic locking using `@Version` (Inventory table)
- **Pattern:** Transactional operations with rollback on conflict
- **Conflict Resolution:** Automatic retry with exponential backoff
- **Trade-off:** Optimistic locking chosen over pessimistic for better throughput and no deadlocks

### Eventual Consistency

**Applied to:** Notifications, Email

- **Tolerance:** Acceptable delivery delay of 1-5 seconds
- **Implementation:** Application events published asynchronously
- **Failure Handling:** Best-effort logging and alerting

### Locking Strategy

```
Optimistic (chosen)
├── Better throughput
├── No lock contention
├── Retry on conflict
└── Suitable for high-traffic checkout

vs. Pessimistic (rejected)
├── Serializable consistency
├── Lock contention in high-traffic
└── Increased deadlock risk
```

---

## Testing Standards

### Unit Tests

- **Scope:** All service layer methods
- **Framework:** JUnit 5 + Mockito
- **Pattern:** Arrange-Act-Assert
- **Mocking:** Mock external dependencies (repositories, HTTP clients)

### Integration Tests

- **Database:** Real PostgreSQL using Testcontainers
- **Scope:** Service + Repository layers
- **Concurrency Proof:** Must include concurrent inventory reservation tests to verify no overselling

### Test Organization

```
com.ecommerce.{service}/
└── service/
    ├── ProductServiceTest.java
    ├── InventoryServiceConcurrencyTest.java
    └── OrderServiceIntegrationTest.java
```

---

## Engineering Quality Standards

### Database Migrations

- **Tool:** Flyway (Spring Boot managed)
- **Pattern:** Version control all schema changes
- **File Naming:** `V{version}__{description}.sql` (e.g., `V1__init.sql`)
- **Requirement:** Every service has its own migration directory

### Logging & Observability

- **Format:** Structured JSON logging
- **Correlation IDs:** Included on all logs for request tracing
- **Levels:**
  - `ERROR`: Failures requiring action
  - `WARN`: Unexpected but recoverable
  - `INFO`: Major business events (orders placed, payments processed)
  - `DEBUG`: Development only
- **No Console Output:** Use structured logging, not `System.out.println()`

### Documentation Requirements

- **ARCHITECTURE.md:** Every service must document:
  - Trade-off decisions and rationale
  - Data flow diagrams
  - Consistency guarantees
  - Resilience strategies

---

## Assumptions

1. All services run locally on separate ports (no containerization initially)
2. PostgreSQL database server is running locally with pgAdmin
3. Development machine has sufficient resources (8+ GB RAM)
4. Java 21 and Maven are installed and configured
5. All inter-service communication is synchronous (no guaranteed delivery queueing)

---

## Constraints

1. **No Docker/Kubernetes:** All services run as local Spring Boot applications
2. **No Redis:** Use Caffeine Cache (in-memory only)
3. **No Message Broker:** Use Spring ApplicationEventPublisher
4. **No Load Balancer:** Single instance per service (local dev)
5. **Database Per Service:** No shared databases (strict microservices)
6. **Single Maven Parent:** All services as child modules

---

## Key Principles Summary

| Principle | Benefit |
|---|---|
| Constructor Injection | Explicit dependencies, testability, immutability |
| Service Interfaces | Contract-driven development, mockability |
| UUIDs for IDs | Distributed generation, no central sequence |
| Instant/UTC Timestamps | No timezone ambiguity, UTC always |
| Never Null | Explicit error handling, no NPE surprises |
| Optimistic Locking | High throughput, no deadlocks |
| Thin Controllers | Testability, separation of concerns |
| RFC 7807 Errors | Standard, tool-friendly error format |

---

## Violation Policy

Violations of these standards must be approved by the architecture review board. All violations must be documented in `ARCHITECTURE.md` with justification.

