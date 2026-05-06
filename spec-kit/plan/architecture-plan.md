# Architecture Plan: E-Commerce Platform

## Overview

This document outlines the complete technical architecture, service topology, technology stack, and inter-service communication patterns for the e-commerce platform.

**Architecture Pattern:** Microservices with API Gateway  
**Deployment Target:** Local development (localhost ports)  
**Data Strategy:** Database per service (polyglot persistence)  
**Last Updated:** April 2026

---

## Scope

- Service inventory and responsibilities
- Technology stack decisions and rationale
- Inter-service communication patterns
- Database design per service
- Resilience and error handling strategies
- Package structure within services

Out of scope:
- Deployment and DevOps pipeline
- Load balancing and auto-scaling
- Monitoring and alerting
- Real-time analytics

---

## System Architecture Overview

```
                    ┌─────────────────┐
                    │   Browser/App   │
                    └────────┬────────┘
                             │ HTTPS
                    ┌────────▼────────┐
                    │  API Gateway    │
                    │   :8080         │
                    │  - JWT Filter   │
                    │  - Rate Limit   │
                    │  - Routing      │
                    └─────────────────┘
                             │
        ┌────────────────────┼────────────────────┬────────────────────┐
        │                    │                    │                    │
   ┌────▼────┐          ┌────▼────┐         ┌────▼────┐          ┌────▼────┐
   │  Auth   │          │ Product │         │Inventory│          │  Order  │
   │ Service │          │ Service │         │ Service │          │ Service │
   │ :8081   │          │ :8082   │         │ :8083   │          │ :8085   │
   └────┬────┘          └────┬────┘         └────┬────┘          └────┬────┘
        │                    │                    │                    │
   ┌────▼─────────────────┐  │                    │              ┌────▼──────────┬──────────────┐
   │PostgreSQL: auth_db   │  │                    │              │  PostgreSQL   │  Events via  │
   │ - Users              │  │                    │              │  order_db     │  AppEvent    │
   │ - Roles              │  │                    │              │  - Orders     │  Publisher   │
   └──────────────────────┘  │                    │              │  - Outbox     │ (async)      │
                             │                    │              └───────────────┴──────────────┘
                        ┌────▼──────────┐         │
                        │PostgreSQL:    │         │
                        │product_db     │         │
                        │ - Products    │         │
                        │ - Categories  │         │
Caffeine Cache          │ - Search idx  │         │
(Product Cache)         └────────────────┘         │
                                                   │
                       ┌──────────────────────────▼──────┐
                       │PostgreSQL: inventory_db         │
                       │ - Current Stock                 │
                       │ - Reservations (with @Version)  │
                       │ - Version Lock                  │
                       └─────────────────────────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Cart Service │  │Payment Service│ │ Notification│  │Eureka Server │
│  :8084       │  │  :8086       │  │ Service     │  │   :8761      │
│              │  │              │  │  :8087      │  │              │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
        │                  │                  │
   ┌────▼────┐        ┌────▼────┐       (stateless)
   │cart_db  │        │payment_db│      (no DB)
   └─────────┘        └──────────┘
```

---

## Service Catalog

### 1. Eureka Server `:8761`

**Purpose:** Service discovery and registration  
**Framework:** Spring Cloud Netflix Eureka

#### Responsibilities
- Register all running microservices
- Provide service registry for Feign clients
- Health checks on all registered services

#### Configuration
- Running locally on port 8761
- All services register on startup
- No authentication required (dev only)

---

### 2. API Gateway `:8080`

**Purpose:** Single entry point, routing, authentication  
**Framework:** Spring Cloud Gateway

#### Responsibilities

- **Request Routing:** Route requests to appropriate backend service
- **JWT Verification:** Verify RS256 JWT tokens using RSA public key; extract `userId` via `claims.getSubject()`, `email`, and `roles` from JWT claims
- **Header Forwarding:** Inject `X-User-Id`, `X-User-Email`, `X-User-Roles`, and `X-Gateway-Secret` headers into every downstream request
- **Rate Limiting:** Implement per-user rate limits (configurable)
- **CORS Headers:** Add cross-origin headers for browser requests
- **Error Handling:** Global exception handling, return RFC 7807 errors
- **Request Logging:** Correlation ID injection
- **401 Response:** Returned if token is missing or invalid on protected routes

#### Public Routes (No Token Required)

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/refresh`

#### Authentication Filter

```
1. Check if request matches a public route → skip JWT check
2. Extract JWT from Authorization: Bearer {token}
3. Verify RS256 signature with RSA public key
4. Check expiration
5. Extract user ID (subject), email, roles from claims
6. Add headers to downstream request:
   - X-User-Id: <userId from claims.getSubject()>
   - X-User-Email: <email from claims>
   - X-User-Roles: <roles from claims>
   - X-Gateway-Secret: <configured secret>
7. Forward to backend service
8. If token missing/invalid → return 401 Unauthorized
```

> **Gateway Secret:** Configured via `GATEWAY_INTERNAL_SECRET` environment variable.
> Downstream services validate this secret to ensure requests came through the gateway.

---

### 3. Auth Service `:8081`

**Purpose:** User authentication, JWT issuance, password management  
**Framework:** Spring Boot + Spring Security + JWT (RS256)

#### Responsibilities
- User registration with validation
- User login with credentials verification
- JWT token generation (access + refresh)
- Token refresh endpoint
- Password hashing with BCrypt
- User role management

#### Database: `auth_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `users` | User accounts, email, password hash |
| `roles` | Role definitions (USER, ADMIN) |
| `user_roles` | User-to-role mapping |

#### Key Endpoints

- `POST /register` → Create user
- `POST /login` → Issue tokens
- `POST /refresh` → New access token
- `GET /me` → Current user info

#### Security Details

- Algorithm: RS256 (RSA 2048-bit asymmetric)
- Access Token Expiry: 15 minutes
- Refresh Token Expiry: 7 days
- Password Hash: BCrypt strength 12
- Key Storage: `keystore.p12` (in config)

---

### 4. Product Service `:8082`

**Purpose:** Product catalog, search, category management  
**Framework:** Spring Boot + JPA

#### Responsibilities
- Create, read, update, delete products (admin only)
- Search products by keyword
- Filter by category, price range, availability
- Pagination on listings
- Product caching with Caffeine

#### Security Model (Gateway-Trust Pattern)

Product-Service does **not** perform JWT/RS256 verification. It trusts the API Gateway:

- **X-Gateway-Secret validation:** Returns `403 Forbidden` if the secret is missing or incorrect
- **SecurityContext built from headers:** `X-User-Id` and `X-User-Roles` are used to construct the Spring Security context
- **No RS256/JWT logic** in product-service — all JWT verification is done at the gateway
- **Direct access blocked:** Requests to port 8082 without a valid `X-Gateway-Secret` return 403

#### Authorization Rules

| Method | Endpoint | Required Role |
|--------|----------|---------------|
| GET | `/products` | USER or ADMIN |
| GET | `/products/{id}` | USER or ADMIN |
| POST | `/products` | ADMIN only |
| PUT | `/products/{id}` | ADMIN only |
| DELETE | `/products/{id}` | ADMIN only |

#### Database: `product_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `products` | Product master data |
| `categories` | Product categories |
| `product_search_idx` | Search index (denormalized) |

#### Caching Strategy

- **Cache Name:** `product-cache`
- **TTL:** 15 minutes
- **Size:** Max 10,000 entries
- **Invalidation:** On product update/delete
- **Key:** Product UUID

#### Key Endpoints

- `GET /api/products` → List with search/filter
- `POST /api/products` → Create (admin only)
- `GET /api/products/{id}` → Single product
- `PUT /api/products/{id}` → Update (admin only)
- `DELETE /api/products/{id}` → Delete (admin only)

#### Performance

- Response Time Target: < 500 ms
- Search indexed via database query
- Pagination: 25 items default

---

### 5. Inventory Service `:8083`

**Purpose:** Stock tracking, reservations, overselling prevention  
**Framework:** Spring Boot + JPA

#### Responsibilities
- Track stock per product
- Reserve stock during checkout
- Confirm reservations on payment success
- Release reservations on failure
- Prevent concurrent overselling
- Support admin stock adjustments

#### Database: `inventory_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `inventory` | Current stock per product (with `@Version` field) |
| `reservations` | Pending stock reservations during checkout |

#### Concurrent Safety Mechanism

**Optimistic Locking Strategy:**

```sql
-- inventory table structure
CREATE TABLE inventory (
    product_id UUID PRIMARY KEY,
    total_stock INTEGER NOT NULL,
    reserved_stock INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT NOW()
);

-- JPA Entity
@Entity
public class Inventory {
    @Id
    private UUID productId;
    private int totalStock;
    private int reservedStock;
    @Version
    private long version;  // Optimistic lock
}
```

**Reservation Workflow:**

```
1. Service calls inventoryService.reserve(productId, quantity)
2. Read inventory record (version = 5)
3. Check: (totalStock - reservedStock) >= quantity
4. If YES: reservedStock += quantity, version++
5. Attempt UPDATE ... WHERE version = 5
6. If version mismatch: throw OptimisticLockException
7. Caller retries (max 3 retries with backoff)
```

**Backoff Strategy:**
- Retry 1: Wait 10 ms
- Retry 2: Wait 20 ms
- Retry 3: Wait 40 ms
- Final: Fail order with "Out of Stock"

#### Key Endpoints

- `POST /api/inventory/reserve` → Reserve for order
- `POST /api/inventory/confirm` → Confirm on payment success
- `POST /api/inventory/release` → Release on payment failure
- `GET /api/inventory/{id}` → Current stock
- `POST /api/inventory/adjust` → Admin adjustment

---

### 6. Cart Service `:8084`

**Purpose:** Persistent shopping cart per user  
**Framework:** Spring Boot + JPA

#### Responsibilities
- Maintain cart per authenticated user
- Add/update/remove items
- Persist across sessions
- Handle concurrent modifications safely
- Calculate subtotals

#### Database: `cart_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `carts` | Cart header per user (with `@Version` for optimistic lock) |
| `cart_items` | Items in cart (product ID, quantity, price snapshot) |

#### Concurrency Handling

- **Optimistic Locking:** Cart header uses `@Version`
- **Concurrent Modification:** 409 Conflict response, client reloads
- **Item-Level:** Each cart line is private to cart ID, no direct contention

#### Key Endpoints

- `GET /api/cart` → View user's cart
- `POST /api/cart/items` → Add item
- `PUT /api/cart/items/{id}` → Update quantity
- `DELETE /api/cart/items/{id}` → Remove item
- `DELETE /api/cart` → Clear cart

---

### 7. Order Service `:8085`

**Purpose:** Order creation, lifecycle management, order history  
**Framework:** Spring Boot + JPA

#### Responsibilities
- Create orders from cart
- Manage order lifecycle (PENDING → PAID → SHIPPED → DELIVERED)
- Handle order cancellation
- Idempotent order placement
- Publish domain events to notification service

#### Database: `order_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `orders` | Order header (status, total, addresses) |
| `order_items` | Items in order (denormalized product details) |
| `order_events` | Event outbox for async notifications |

#### Idempotency Implementation

```
Header: Idempotency-Key: {UUID}

1. On order creation request:
   - Check if existing order with same idempotency key
   - If YES: return existing order (409 or 200)
   - If NO: proceed with creation, store key + order ID
2. Subsequent requests with same key return same order
3. Key expires after 30 days
```

#### Event Publishing

Orders emit events via Spring ApplicationEventPublisher:
- `OrderCreatedEvent` → Notification Service
- `OrderPaidEvent` → Notification Service
- `OrderShippedEvent` → Notification Service

#### Order Workflow

```
1. POST /api/orders (with cart items, address)
2. Validate cart not empty
3. Call inventory-service: reserve stock
4. Call payment-service: process payment
5. On payment success:
   - Create Order entity (status = PAID)
   - Publish OrderPaidEvent
   - Clear cart
   - Return order
6. On payment failure:
   - Call inventory-service: release reservation
   - Throw PaymentFailedException
```

#### Key Endpoints

- `POST /api/orders` → Create order
- `GET /api/orders` → List user's orders (with status filter)
- `GET /api/orders/{id}` → Order details
- `POST /api/orders/{id}/cancel` → Cancel order
- `PUT /api/orders/{id}/status` → Update status (admin only)

---

### 8. Payment Service `:8086`

**Purpose:** Mock payment processing with retry logic  
**Framework:** Spring Boot

#### Responsibilities
- Process payments (mock implementation)
- Retry failed payments with exponential backoff
- Idempotency key support (prevent duplicate charges)
- Success/failure simulation based on config

#### Database: `payment_db` (PostgreSQL)

| Table | Purpose |
|---|---|
| `payments` | Payment transaction history |
| `payment_idempotency_keys` | Idempotency keys + results |

#### Payment Configuration

```yaml
payment:
  mock:
    success-rate: 0.95  # 95% of payments succeed
    timeout-ms: 5000
    retry:
      max-attempts: 3
      backoff-multiplier: 2
      initial-delay-ms: 1000  # 1s, 2s, 4s
```

#### Retry Strategy

```
Attempt 1: Immediate
Attempt 2: After 1 second
Attempt 3: After 2 seconds
Final: After 4 seconds

Total max wait: 7 seconds per order
```

#### Idempotency Implementation

```
Header: Idempotency-Key: {UUID}

1. Check: existing result for this key?
  - If YES: return stored result (no charge)
  - If NO: proceed
2. Process payment
3. Store key + result
4. Return result
```

#### Payment Response

```json
{
  "transactionId": "TXN-2026-004567",
  "status": "SUCCESS|FAILED",
  "amount": 99.99,
  "timestamp": "2026-04-30T14:23:45Z",
  "reason": "Insufficient funds" // if failed
}
```

#### Key Endpoints

- `POST /api/payments/process` → Process payment
- `GET /api/payments/{id}` → Payment details
- `POST /api/payments/{id}/retry` → Manual retry

---

### 9. Notification Service `:8087`

**Purpose:** Event-driven notifications (mock email)  
**Framework:** Spring Boot (stateless)

#### Responsibilities
- Listen for application events
- Format and send mock emails (log to console)
- Ready for SMTP integration
- Best-effort delivery (eventual consistency)

#### No Database

- Completely stateless
- Event listener pattern
- No persistence needed

#### Event Listeners

```java
@EventListener
public void onOrderPaid(OrderPaidEvent event) {
  // Send "Order Confirmed" email
}

@EventListener
public void onPaymentSuccess(PaymentSuccessEvent event) {
  // Send "Payment Received" email
}

@EventListener
public void onPaymentFailed(PaymentFailedEvent event) {
  // Send "Payment Failed" email
}

@EventListener
public void onOrderShipped(OrderShippedEvent event) {
  // Send "Order Shipped" email
}
```

#### Mock Email Output

```
[MOCK EMAIL SERVICE]
To: customer@example.com
From: noreply@ecommerce.local
Subject: Order ORD-2026-001234 Confirmed
Sent: 2026-04-30T14:23:45Z

---
Your order has been confirmed.
Order #: ORD-2026-001234
Total: $99.99
Estimated Delivery: May 5, 2026
---
```

#### Key Endpoints

- `GET /health` → Health check only

---

## Inter-Service Communication

### Synchronous Communication (OpenFeign)

Services call each other directly via REST + Feign:

| Caller | Callee | Use Case |
|---|---|---|
| Order Service | Inventory Service | Reserve/confirm/release stock |
| Order Service | Payment Service | Process payment + retry |
| Order Service | Auth Service | Validate user |
| Cart Service | Product Service | Validate product exists |

#### Feign Configuration

```java
@FeignClient(
    name = "inventory-service",
    url = "http://localhost:8083",
    configuration = FeignConfig.class
)
public interface InventoryClient {
    @PostMapping("/api/inventory/reserve")
    ReservationResponse reserve(@RequestBody ReservationRequest request);
}
```

#### Resilience (Resilience4j)

- **Circuit Breaker:** Fail fast if service down
- **Retry:** Automatic retry on transient failures
- **Timeout:** 5-second timeout per request
- **Fallback:** Graceful error response

### Asynchronous Communication (ApplicationEventPublisher)

Order Service publishes events that Notification Service listens to:

```
Order Service
  ├─ publishEvent(OrderPaidEvent)
  └─ publishEvent(OrderShippedEvent)
       ↓
Spring ApplicationEventPublisher
       ↓
Notification Service
  ├─ @EventListener(OrderPaidEvent)
  └─ @EventListener(OrderShippedEvent)
       ↓
Mock Email Output
```

#### Advantages

- In-process: No network latency
- Transactional: Events published in same transaction as order creation
- Decoupled: Notification Service independent lifecycle

---

## Technology Stack

### Backend Framework

| Component | Technology | Version | Rationale |
|---|---|---|---|
| Framework | Spring Boot | 3.5.14 | Latest, modern Java enterprise |
| Language | Java | 21 | LTS release, modern features |
| Web | Spring MVC | 6.x | Built into Spring Boot |
| Security | Spring Security | 6.x | Industry standard |
| Data | Spring Data JPA | 3.x | ORM abstraction |
| Microservices | Spring Cloud | 2024.0.x | Service discovery, gateway |
| Discovery | Eureka | - | Service registry |
| Gateway | Spring Cloud Gateway | - | API routing + filters |
| Service Calls | OpenFeign | - | REST client with Eureka integration |
| Resilience | Resilience4j | - | Circuit breaker + retry |
| Database | PostgreSQL | 14+ | Reliable SQL database |
| Connection Pool | HikariCP | 5.x | High-performance pooling |
| Cache | Caffeine | 3.x | In-process cache, Spring Boot integrated |
| JWT | java-jwt | 4.x | Token creation/validation |
| Password Hash | BCrypt | Spring Security bundled | Secure hashing |
| Migrations | Flyway | 9.x | Schema versioning |
| Mapping | MapStruct | 1.5.x | DTO ↔ Entity mapping |
| Validation | Hibernate Validator | 8.x | Bean validation |
| Testing | JUnit 5 | Built-in | Test framework |
| Mocking | Mockito | - | Mock objects |
| Integration Tests | Testcontainers | 1.20.x | Real containers |
| Build | Maven | 3.8.x | Dependency + build management |

### Frontend (Optional - Reference)

| Component | Technology | Purpose |
|---|---|---|
| Framework | Angular | 17+ |
| Components | Standalone | No NgModules |
| Styling | Tailwind CSS | Utility-first CSS |
| State | Angular Signals | Reactive |
| HTTP | HttpClient | REST calls |
| SSR | Angular SSR | Server-side rendering |
| Interceptors | HTTP Interceptor | JWT, error handling |

---

## Database Strategy

### Database Per Service Pattern

```
Each microservice owns its data:
- No direct foreign keys between services
- Synchronization via Feign calls
- Eventual consistency for read replicas
```

### Schema per Service

| Service | Database | Purpose |
|---|---|---|
| Auth Service | auth_db | Users, roles |
| Product Service | product_db | Products, categories |
| Inventory Service | inventory_db | Stock, reservations |
| Cart Service | cart_db | Carts, items |
| Order Service | order_db | Orders, items, events |
| Payment Service | payment_db | Transactions, idempotency keys |
| Notification Service | (none) | Stateless listener |

### Connection Pooling

- **Pool Size:** Min 10, Max 20 per service
- **Validation:** Idle connections validated every 5 minutes
- **Timeout:** Connection timeout 10 seconds

---

## Package Structure per Service

Every Spring Boot service follows this structure:

```
com.ecommerce.{servicename}/
├── config/
│   ├── SecurityConfig.java
│   ├── CacheConfig.java
│   ├── FeignConfig.java
│   └── JpaConfig.java
├── controller/
│   ├── ProductController.java
│   ├── InventoryController.java
│   └── ...
├── service/
│   ├── IProductService.java          (interface)
│   ├── ProductService.java           (impl)
│   ├── IInventoryService.java        (interface)
│   └── InventoryService.java         (impl)
├── repository/
│   ├── ProductRepository.java        (Spring Data JPA)
│   ├── InventoryRepository.java
│   └── ...
├── domain/
│   ├── Product.java                  (JPA entity)
│   ├── Inventory.java
│   └── ...
├── dto/
│   ├── ProductRequest.java           (Java record)
│   ├── ProductResponse.java          (Java record)
│   ├── InventoryRequest.java
│   └── InventoryResponse.java
├── mapper/
│   ├── ProductMapper.java            (MapStruct)
│   └── InventoryMapper.java
├── exception/
│   ├── GlobalExceptionHandler.java   (@ControllerAdvice)
│   ├── ProductNotFoundException.java
│   ├── InsufficientStockException.java
│   └── ...
├── event/
│   ├── DomainEvent.java              (abstract base)
│   ├── OrderPaidEvent.java
│   ├── OrderShippedEvent.java
│   └── ...
├── security/
│   ├── JwtAuthenticationFilter.java    (validates X-Gateway-Secret, builds SecurityContext from headers)
│   ├── SecurityConfig.java
│   └── ...
└── {ServiceName}Application.java     (Spring Boot main)
```

---

## Error Handling & Resilience

### RFC 7807 ProblemDetail Format

**All errors** returned in this standard format:

```json
{
  "type": "https://api.ecommerce.local/errors/product-not-found",
  "title": "Product Not Found",
  "status": 404,
  "detail": "Product with ID 123e4567-e89b-12d3-a456-426614174000 was not found in the catalog",
  "instance": "/api/products/123e4567-e89b-12d3-a456-426614174000",
  "timestamp": "2026-04-30T14:23:45Z"
}
```

### HTTP Status Codes

| Code | Meaning | Example |
|---|---|---|
| 200 | Success | Product retrieved |
| 201 | Created | Order created |
| 400 | Bad Request | Invalid input data |
| 401 | Unauthorized | Missing/invalid JWT |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Product doesn't exist |
| 409 | Conflict | Concurrent cart modification |
| 422 | Unprocessable | Validation failed |
| 500 | Internal Error | Uncaught exception |
| 503 | Unavailable | Circuit breaker open |

### Circuit Breaker Pattern

All Feign clients use Resilience4j:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventory-service:
        slidingWindowSize: 10
        failureThresholdPercentage: 50
        waitDurationInOpenState: 10000  # 10 seconds
      payment-service:
        slidingWindowSize: 20
        failureThresholdPercentage: 50
        waitDurationInOpenState: 30000  # 30 seconds
  retry:
    instances:
      inventory-service:
        maxAttempts: 3
        waitDuration: 100
      payment-service:
        maxAttempts: 3
        waitDuration: 1000
```

---

## Logging & Observability

### Structured Logging

All logs use JSON format with correlation ID:

```json
{
  "timestamp": "2026-04-30T14:23:45.123Z",
  "level": "INFO",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "service": "order-service",
  "message": "Order placed successfully",
  "orderId": "ORD-2026-001234",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "totalAmount": 99.99
}
```

### Log Levels

- **ERROR:** Actionable failures (payment declined, order failed)
- **WARN:** Unexpected but recoverable (retry succeeded after failure)
- **INFO:** Business events (order created, payment processed)
- **DEBUG:** Development only (detailed state dumps)

---

## Deployment & Configuration

### Port Allocation

```
8761  → Eureka Server
8080  → API Gateway
8081  → Auth Service
8082  → Product Service
8083  → Inventory Service
8084  → Cart Service
8085  → Order Service
8086  → Payment Service
8087  → Notification Service
```

### Environment Configuration

Each service has `application.yaml`:

```yaml
spring:
  application:
    name: order-service
  jpa:
    hibernate:
      ddl-auto: validate
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: postgres
    password: ${DB_PASSWORD}

server:
  port: 8085

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

---

## Assumptions

1. PostgreSQL server running locally on port 5432
2. pgAdmin available at http://localhost:5050 for DB management
3. All services start within 30 seconds
4. Network latency < 100 ms (local machine)
5. No external SMTP configured (mock email only)
6. Java 21 and Maven installed
7. No production firewall rules (local dev only)

---

## Constraints

1. **No Docker:** All services as local processes
2. **No Kubernetes:** Single-machine deployment
3. **No Load Balancers:** One instance per service
4. **No Redis:** Caffeine in-memory only
5. **No RabbitMQ:** ApplicationEventPublisher only
6. **Single Zone:** All on localhost
7. **No Caching Invalidation:** TTL-based only

---

## Trade-off Decisions & Rationale

### Decision 1: Optimistic vs. Pessimistic Locking

**Chosen: Optimistic**

| Aspect | Optimistic | Pessimistic |
|---|---|---|
| Throughput | Higher | Lower |
| Lock Contention | None | Possible |
| Deadlock Risk | None | Yes |
| Retry Logic | Required | Not needed |
| Best For | High-traffic | Strong consistency |

**Rationale:** E-commerce likely to have concurrent requests. Optimistic avoids lock contention and provides 95%+ success on first try.

### Decision 2: Cache Strategy (Caffeine vs Redis)

**Chosen: Caffeine**

| Aspect | Caffeine | Redis |
|---|---|---|
| Setup | None | Needs server |
| Deployment | In-process | Separate |
| Latency | < 1 ms | 10-20 ms |
| Memory | Single JVM | External |
| Scalability | Per-instance | Shared |

**Rationale:** Development environment, single machine. Caffeine eliminates operational overhead.

### Decision 3: Events (ApplicationEventPublisher vs RabbitMQ)

**Chosen: ApplicationEventPublisher**

| Aspect | AppEventPublisher | RabbitMQ |
|---|---|---|
| Setup | None | Needs broker |
| Latency | < 1 ms | 10-50 ms |
| Persistence | None | Durable queues |
| Guaranteed Delivery | No | Yes |
| Scaling | Single instance | Multiple brokers |

**Rationale:** Notifications are non-critical, eventual consistency acceptable. In-process events simpler for dev.

### Decision 4: Database Per Service

**Chosen: Yes**

| Aspect | Per Service | Shared |
|---|---|---|
| Autonomy | Full | Limited |
| Consistency | Eventual | Strong |
| Scaling | Independent | Coupled |
| Deployment | Flexible | Rigid |
| Data Access | Via API | Direct query |

**Rationale:** Microservices requires independent data ownership. Prevents tight coupling.

---

## Future Scalability

To scale to production:

1. **Docker & Kubernetes:** Containerize each service
2. **Cloud Database:** Managed PostgreSQL (AWS RDS, Azure Database)
3. **Redis:** For distributed caching and session store
4. **Message Broker:** RabbitMQ or Apache Kafka for event durability
5. **Load Balancer:** NGINX or cloud LB in front of Gateway
6. **Monitoring:** ELK Stack, Prometheus, Grafana
7. **CDN:** For static product images
8. **Real Payment Provider:** Payment Processing Service integration


