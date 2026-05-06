# Glossary: E-Commerce Platform Terms

A comprehensive reference of terms, acronyms, and concepts used throughout the Spec-Kit.

---

## Architecture & Design Patterns

### Circuit Breaker
A design pattern that prevents cascading failures when calling external services. If a service is down, the circuit "opens" and subsequent calls fail fast without waiting for timeout.

**When:** Resilience4j implements this for all Feign clients  
**Example:** If Inventory Service is down, Order Service fails fast with "Service unavailable"

### Microservices
Software architecture pattern where applications are built as loosely coupled, independently deployable services.

**Benefits:** Independent scaling, deployment flexibility, technology choice per service

### Service Discovery
The process where services automatically register themselves and can be found by other services.

**Implemented by:** Eureka Server at `:8761`

### REST API
Representational State Transfer - architectural style for web services using HTTP methods (GET, POST, PUT, DELETE) and standard status codes.

**All services:** Communicate via REST APIs through API Gateway `:8080`

---

## Data & Database

### Flyway
Database migration tool that manages schema versioning via SQL files.

**Usage:** Each service has `V{version}__{description}.sql` migrations  
**Example:** `V1__init_inventory_db.sql`, `V2__add_reservation_table.sql`

### Repository Pattern
Data access abstraction layer that encapsulates database queries.

**Implementation:** Spring Data JPA interfaces (no SQL writing needed)  
**Example:** `ProductRepository.findByNameContainsIgnoreCase(name)`

### Optimistic Locking
Concurrency control technique using a version field. Instead of locking rows, we detect concurrent modifications and retry.

**JPA Implementation:** `@Version` annotation on an entity field  
**Flow:**
1. Read entity with version = 5
2. Modify entity
3. Try to write: `UPDATE users SET ... WHERE id=X AND version=5`
4. If version changed: OptimisticLockingException
5. Retry with exponential backoff

**Advantage:** No database locks, better throughput  
**Trade-off:** Need retry logic in application

### Pessimistic Locking
Alternative locking strategy that locks rows immediately. More straightforward but can cause contention.

**Why we rejected it:** High-traffic scenarios would see lock contention and deadlocks

### Database-Per-Service
Microservices pattern where each service has its own dedicated database.

**Benefit:** Services are autonomous, can use different technologies  
**Trade-off:** No transactions across services, data consistency must be managed

### HikariCP
High-performance JDBC database connection pool.

**Configuration:** Min 10, Max 20 connections per service  
**Purpose:** Reuse connections, reduce overhead

---

## Caching

### Caffeine Cache
In-memory caching library built into Spring Boot. No external infrastructure needed.

**Configuration:** 15-minute TTL, max 10,000 entries  
**Invalidation:** On update/delete, clears entire cache  
**Alternative rejected:** Redis (adds operational complexity)

### Cache Invalidation
Process of removing stale cache entries after data changes.

**Strategy:** `@CacheEvict` on update operations  
**Example:** When product is updated, clear entire product cache (all instances see no cache)

---

## Security

### JWT (JSON Web Token)
Stateless authentication token containing user information, signed digitally.

**Format:** `header.payload.signature`  
**Signature Algorithm:** RS256 (asymmetric RSA-2048)  
**Verification:** Only at the API Gateway (port 8080) using the RSA public key  
**Storage:** Browser localStorage (frontend)  
**Expiry:** Access token 15 minutes, Refresh token 7 days  
**Downstream services:** Do NOT verify JWT — they trust the gateway via X-Gateway-Secret header

### RS256 (RSA Signature with SHA-256)
Asymmetric cryptographic algorithm using public-private key pair.

**Private Key:** Stored in keystore, used only by Auth Service to sign tokens  
**Public Key:** Shared with Gateway, used to verify tokens  
**Benefit:** Anyone can verify token, but only Auth Service can create tokens

### BCrypt
Password hashing algorithm with salt and stretching (slow by design).

**Strength:** 12 (out of 4-31 range, higher = slower/more secure)  
**Purpose:** If database compromised, passwords can't be reversed  
**Verification:** Compare input hash with stored hash (not reversed)

### @PreAuthorize
Spring Security annotation to check roles before method execution.

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteProduct(UUID id) { }
```

---

## APIs & Communication

### API Gateway
Single entry point for all client requests. Handles routing, JWT verification, and header forwarding.

**Technology:** Spring Cloud Gateway  
**Port:** `:8080`  
**Responsibilities:**
- Verify RS256 JWT tokens using RSA public key
- Extract userId, email, roles from JWT claims
- Inject X-User-Id, X-User-Email, X-User-Roles, X-Gateway-Secret headers into downstream requests
- Return 401 if token is missing or invalid
- Route to backend service
- Public routes (no token): /api/auth/login, /api/auth/register, /api/auth/refresh

### Gateway Trust Model
Security pattern where the API Gateway is the sole point of JWT verification, and downstream services trust the gateway.

**How it works:**
1. Gateway verifies JWT and extracts user info
2. Gateway injects X-User-Id, X-User-Email, X-User-Roles, X-Gateway-Secret into headers
3. Downstream services validate X-Gateway-Secret (not JWT)
4. Downstream services build SecurityContext from headers
5. Direct access without X-Gateway-Secret returns 403

**Gateway Secret:** Configured via `GATEWAY_INTERNAL_SECRET` environment variable

### OpenFeign
Declarative HTTP client that simplifies inter-service REST calls.

**Feature:** Automatic service discovery via Eureka  
**Example:**
```java
@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/inventory/reserve")
    ReservationResponse reserve(@RequestBody ReservationRequest req);
}
```

### Feign Client
Interface decorated with Feign annotations for making HTTP calls.

**Resilience:** Automatically integrated with Resilience4j circuit breaker  
**Error Handling:** Fallback methods or exception throwing

### Idempotency
Property where calling an operation multiple times has the same effect as calling it once.

**Use Case:** Duplicate order placement requests should return same order (no double charge)  
**Implementation:** Store `Idempotency-Key` header + response, return cached response if key already exists  
**Benefit:** Network retries safe, payment calls safe

---

## Events & Messaging

### Domain Event
Business event raised by a service indicating something important happened.

**Examples:**
- `OrderPaidEvent` - Order payment succeeded
- `OrderShippedEvent` - Order shipped by warehouse
- `PaymentFailedEvent` - Payment attempt failed

### ApplicationEventPublisher
Spring Framework mechanism for in-process event publishing and listening.

**Implementation:** `publisher.publishEvent(new OrderPaidEvent(...))`  
**Listening:** `@EventListener public void onOrderPaid(OrderPaidEvent event)`  
**Publishing:** Happens in same transaction, synchronous  
**Use Case:** Notifications listening to order events

### Event Listener
Component that reacts to published events.

**In Notification Service:**
```java
@EventListener
public void onOrderPaid(OrderPaidEvent event) {
    emailService.sendOrderConfirmation(event);
}
```

---

## E-Commerce Specific

### Shopping Cart
Temporary storage of items customer wants to buy before checkout.

**Persistence:** Stored in `cart_db` (survives page refresh)  
**Lifecycle:** Cleared after successful order or manually cleared  
**Concurrency:** Optimistic locking to handle simultaneous modifications

### Cart Item
Single line in shopping cart with product reference and quantity.

**Fields:** Product ID, quantity, price snapshot (locked at add time)

### Order Lifecycle
State machine representing order progression.

**States:**
```
PENDING (user added items)
  ↓
AWAITING_PAYMENT (user at checkout)
  ↓
PAID (payment succeeded) ← Can still cancel from PENDING/AWAITING_PAYMENT
  ↓
SHIPPED (warehouse shipped)
  ↓
DELIVERED (customer received)

Alternative: Any state → CANCELLED
```

### Order Idempotency
Orders can't be created twice even with duplicate requests.

**Mechanism:** `Idempotency-Key` header equals `UUID` unique to order intent  
**Storage:** `OrderIdempotencyKey` table maps key to order ID  
**Benefit:** Retry network requests safely

### Inventory Reservation
Temporary hold on stock during checkout, confirmed on payment success.

**States:**
- **Reserved:** Subtracted from available stock during checkout
- **Confirmed:** Permanently removed from total stock on payment success
- **Released:** Returned to available stock if payment fails

### Overselling Prevention
System guarantee that inventory never sells more units than available.

**Mechanism:** Optimistic locking + retry in Inventory Service  
**Proof:** Concurrency test with 100 concurrent requests on 50-unit inventory  
**Result:** Exactly 50 succeed, 50 fail with "Out of stock"

### Fulfillment
Process of warehouse picking, packing, and shipping order items.

**Not implemented:** This system only tracks orders, not actual warehouse operations  
**Real-world:** Would integrate with warehouse management system

### Return/Refund
Customer returning unwanted items or refund after payment.

**Status:** Not in Phase 1 (future feature)

---

## REST & HTTP

### HTTP Status Codes
Standardized response codes indicating request result.

**2xx Success:**
- 200: OK (generic success)
- 201: Created (resource created)

**4xx Client Error:**
- 400: Bad Request (invalid input)
- 401: Unauthorized (missing/invalid JWT)
- 403: Forbidden (insufficient permissions)
- 404: Not Found (resource doesn't exist)
- 409: Conflict (concurrent modification)
- 422: Unprocessable Entity (validation failed)

**5xx Server Error:**
- 500: Internal Server Error (unexpected exception)
- 503: Service Unavailable (downstream service down)

### RFC 7807 ProblemDetail
Standard format for error responses (adopted by Spring Boot).

**Advantage:** Consistent, parseable by tools  
**Example:**
```json
{
  "type": "https://api.example.com/errors/product-not-found",
  "title": "Product Not Found",
  "status": 404,
  "detail": "Product with ID ... not found",
  "timestamp": "2026-04-30T14:23:45Z"
}
```

---

## Testing

### Unit Test
Tests a single method in isolation with mocked dependencies.

**Framework:** JUnit 5 + Mockito  
**Scope:** Service layer methods only  
**Pattern:** Arrange-Act-Assert

### Integration Test
Tests multiple components working together with real database.

**Framework:** JUnit 5 + Testcontainers  
**Database:** Real PostgreSQL in Docker container  
**Scope:** Service + Repository layers

### Testcontainers
Library that starts Docker containers for tests (PostgreSQL, Redis, etc.).

**Usage:** Real database without local installation  
**Cleanup:** Containers automatically removed after test  
**Benefit:** Test against actual database behavior, not mocks

### Concurrency Test
Test verifying system behavior under concurrent requests.

**Scenario:** 100 threads attempting same operation simultaneously  
**Verification:** Race conditions not occurring, system stays consistent

---

## Maven & Build

### Maven
Build automation tool managing dependencies, compilation, testing, packaging.

**Project Structure:** Parent `pom.xml` + child modules per service  
**Dependency Management:** Centralized versions in parent  
**Phases:** compile → test → package → install

### pom.xml
Maven project configuration file specifying:
- Dependencies (libraries)
- Plugins (build tools)
- Version management
- Module structure

### Module
Sub-project in Maven parent-child hierarchy.

**Each service:** Its own module with `pom.xml`  
**Parent:** Root `pom.xml` with spring-boot-starter-parent

---

## Java & Spring

### Spring Boot
Framework simplifying Spring application development.

**Version:** 3.5.14  
**Features:** Auto-configuration, embedded server, production-ready  
**Advantages:** Convention over configuration

### Spring Cloud
Framework for building distributed systems components.

**Services:**
- Eureka (service discovery)
- Gateway (API gateway)
- Netflix stack integration

### Dependency Injection
Design pattern where objects are given their dependencies rather than creating them.

**Method:** Constructor injection (required in this project)  
**Benefit:** Testability, immutability, explicit dependencies

### Component Scanning
Automatic discovery of beans (Spring-managed objects) in code.

**Indicator:** `@Component`, `@Service`, `@Repository` annotations  
**Scope:** Only finds classes in same package or sub-packages

### DTO (Data Transfer Object)
Object used for transferring data between API layers and internal layers.

**Implementation:** Java records (lightweight, immutable)  
**Purpose:** Decouple API contract from domain model

### Mapper
Utility that converts between DTOs and entities.

**Tool:** MapStruct (code generation, not reflection)  
**Example:** Converts `ProductEntity` ↔ `ProductResponse`

### Entity
Domain class mapped to database table (JPA).

**Annotations:** `@Entity`, `@Table`, `@Column`  
**Lifecycle:** Managed by EntityManager, transitions between states

---

## Development & Deployment

### Port
Network endpoint where service listens for requests.

**Allocation:**
- 8761: Eureka
- 8080: Gateway
- 8081-8087: Individual services

### Spring Profile
Configuration variant for different environments.

**Common:** dev, test, prod  
**Usage:** `application-{profile}.yaml` files  
**Example:** `application-prod.yaml` has production database URL

### Continuous Integration
Automated testing and building on every code commit.

**Not implemented:** Part of future DevOps setup

### Local Development
Development environment on developer machine.

**Setup:** PostgreSQL local, All services as local processes  
**No Docker/Kubernetes:** All 9 services run on one machine

---

## Consistency & Transactions

### ACID
Database transaction guarantee (Atomicity, Consistency, Isolation, Durability).

**Applied:** Order creation, payment processing  
**Isolation Level:** Recommended SERIALIZABLE for payments

### Transactional Consistency
Guarantee that database transitions from one valid state to another.

**Implementation:** `@Transactional` annotation wraps method in transaction  
**Rollback:** Entire transaction undone if exception occurs

### Eventual Consistency
Guarantee that data will eventually be consistent across services, not immediately.

**Used for:** Notifications, email (1-5 second delay acceptable)  
**Advantage:** Better availability, no blocking coordination

---

## Performance & Load

### Throughput
Number of requests system handles per second.

**Target:** Optimistic locking chosen for higher throughput  
**Compared to:** Pessimistic locking would reduce throughput on concurrent orders

### Latency
Time from request sent to response received.

**Target:** <500ms for search, <1s for most operations  
**Not counting:** Payment processing (simulation)

### Connection Pooling
Reusing database connections instead of creating new ones.

**Tool:** HikariCP (standard for Spring Boot)  
**Configuration:** Min 10, Max 20 connections

---

## Infrastructure (Not Used)

### Docker
Containerization technology for isolated application environments.

**Status:** NOT USED in this project (local dev only)

### Kubernetes
Orchestration platform for managing containerized applications.

**Status:** NOT USED in this project

### Redis
In-memory data store used for caching and session storage.

**Status:** NOT USED (using Caffeine instead)

### RabbitMQ
Message queue broker for asynchronous messaging.

**Status:** NOT USED (using ApplicationEventPublisher instead)

---

## Abbreviations

| Acronym | Full Term | Context |
|---------|-----------|---------|
| JWT | JSON Web Token | Authentication |
| RS256 | RSA Signature with SHA-256 | JWT algorithm |
| ACID | Atomicity, Consistency, Isolation, Durability | Database transactions |
| CRUD | Create, Read, Update, Delete | Basic operations |
| DTO | Data Transfer Object | API contracts |
| ORM | Object-Relational Mapping | JPA |
| SQL | Structured Query Language | Database queries |
| UUID | Universally Unique Identifier | Entity IDs |
| HTTP | HyperText Transfer Protocol | Web communication |
| REST | Representational State Transfer | API style |
| JSON | JavaScript Object Notation | Data format |
| XML | Extensible Markup Language | Config file format |
| JAR | Java Archive | Compiled code package |
| POM | Project Object Model | Maven config file |
| JPA | Java Persistence API | ORM standard |
| ORM | Object-Relational Mapping | Database abstraction |
| EOF | End of File | File marker |
| UTC | Coordinated Universal Time | Time zone (always used) |

---

**Version:** 1.0  
**Last Updated:** April 2026  
**Scope:** Complete e-commerce platform terminology

**Feedback:** If a term is unclear or missing, add it here for team consistency.

