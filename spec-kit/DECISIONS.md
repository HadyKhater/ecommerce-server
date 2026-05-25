# Key Technical Decisions & Trade-offs — DECISIONS.md

This document details the core architectural decisions, concurrency-control mechanisms, and communication patterns established in the E-Commerce platform, along with their associated trade-offs.

---

## 1. Decentralized Microservices Architecture

### Decision
The platform is built as a set of logical services integrated via service discovery.

```
[ Eureka Server :8761 ]
       ▲
       │ Dynamic Registration
[ API Gateway :8080 ] ──► [ Downstream Microservices (:8081 - :8087) ]
```

### Rationale
- **Technology Isolation:** Services can choose frameworks and database optimizations suited to their domains (e.g., in-memory Caffeine cache in `Product Service`, STOMP message brokering in `Notification Service`).
- **Independent Scaling:** High-traffic services like `Product Service` or `Cart Service` can scale horizontally without replicating order execution or authorization components.
- **Fail-Fast Boundaries:** Downstream microservices execute inside isolated processes. If `Payment Service` runs out of threads, Resilience4j circuit breakers in `Order Service` open to reject new checkouts immediately, preserving database sanity.

### Trade-offs
- **Complexity:** Running 9 microservices locally requires managing service discovery, client-side load balancing, configuration distribution, and distributed request logging.
- **Latency Overhead:** Inter-service synchronous REST requests (via Feign Client) introduce HTTP serialization/network latencies compared to monolithic function calls.

---

## 2. Consistency Boundaries

The application divides consistency requirements into two distinct boundaries based on domain criticality:

```
┌────────────────────────────────────────┐     ┌────────────────────────────────┐
│   Strong Consistency Boundary          │     │  Eventual Consistency Boundary │
│                                        │     │                                │
│  - Order Checkout Reservation          ├────►│  - WebSocket Push Updates      │
│  - Stock Allocation / Lock Checks      │     │  - In-App Persistent History   │
│  - Coupon Usage Validation             │     │                                │
└────────────────────────────────────────┘     └────────────────────────────────┘
```

### Strong Consistency (Inventory, Checkout, Coupons)
- **Design:** Double-purchasing, overselling, and promo-code budget evasion must be prevented at all costs. Transactions within `inventory-service` and `order-service` operate on local databases using `@Transactional` barriers.
- **Verification:** Stock is reserved synchronously via Feign during checkout creation. If the reservation fails, the transaction rolls back, and no order is placed.

### Eventual Consistency (Notifications, User Alerts)
- **Design:** Order success notifications and status update pushes do not require strong transaction participation.
- **Verification:** The `Order Service` registers order updates in its local database, then dispatches an asynchronous call to the `Notification Service` Feign client. If the notification client fails, order execution succeeds, and the error is caught and logged safely (`sendNotificationSafe`), ensuring checkout is not blocked by notification failures.

---

## 3. Optimistic Locking Strategy (`@Version`)

To support high-concurrency cart manipulations and stock reservations, the platform rejects pessimistic row locking (`SELECT FOR UPDATE`) in favor of optimistic concurrency control.

### Implementation
JPA entities for critical data (Inventory, Cart, Promotion, and Banners) declare a `@Version` attribute:

```java
@Version
private Long version;
```

When a transaction attempts to modify a row:
1. Hibernate queries the record, capturing its primary state and `version` number.
2. Upon commit, Hibernate issues a SQL query asserting the version has not changed:
   ```sql
   UPDATE inventory SET quantity_available = ?, version = version + 1 
   WHERE id = ? AND version = ?
   ```
3. If another worker thread modified the row in the interim, the assertion returns `0` affected rows, prompting Hibernate to throw an `OptimisticLockingFailureException`.

### Trade-offs
- **High Read Throughput:** Unlike pessimistic locking, which blocks concurrent threads reading stock or cart items, optimistic locking allows lock-free reads.
- **Conflict Handling:** The system relies on retry mechanisms (e.g., client-side retries or Spring `@Retryable` annotations) or handles failures by alerting users that their action collided, requiring them to try again.

---

## 4. Two-Phase Commit (2PC) Simulation for Orders

Standard microservices patterns recommend sagas for distributed transactions. To guarantee stock accuracy while validating payments without complex saga choreographies, this platform implements a custom **Two-Phase Commit** simulation.

```
[ Client ]               [ Order Service ]             [ Inventory Service ]         [ Payment Service ]
    │                            │                               │                            │
    │ 1. POST /orders/reserve    │                               │                            │
    ├───────────────────────────►│                               │                            │
    │                            │ 2. Feign: POST /reserve       │                            │
    │                            ├──────────────────────────────►│                            │
    │                            │                               │ (Stock Moved to Reserved)  │
    │                            │◄──────────────────────────────┤                            │
    │                            │                               │                            │
    │ 3. Return Order (RESERVED) │                               │                            │
    │◄───────────────────────────┤                               │                            │
    │                            │                               │                            │
    │ 4. Pay via Stripe/Mock     │                               │                            │
    ├────────────────────────────────────────────────────────────────────────────────────────►│
    │                            │                               │                            │
    │                            │ 5. Confirm order (Webhook)    │                            │
    │                            │◄───────────────────────────────────────────────────────────┤
    │                            │                               │                            │
    │                            │ 6. Mark Status: CONFIRMED     │                            │
    │                            │                               │                            │
```

### Phase 1: Order Reservation
- The client submits a checkout request via `POST /orders/reserve`.
- The `Order Service` creates an order record with status `OrderStatus.RESERVED`.
- It issues a synchronous Feign call to the `Inventory Service` to deduct stock from `quantityAvailable` and add it to `quantityReserved`.
- **Outcome:** Stock is reserved for 2 minutes. The user is returned the reserved order details.

### Phase 2: Confirmation / Commit
- The user proceeds to payment (Stripe checkout session or simulated gateway).
- Upon successful payment, a Stripe webhook callback or simulated success endpoint triggers `PUT /orders/{id}/confirm`.
- The `Order Service` changes the status to `OrderStatus.CONFIRMED`.
- **Outcome:** The transaction commits, and stock reservation becomes permanent.

### Failure / Rollback
- If payment fails, the frontend or callback triggers `PUT /orders/{id}/cancel-reservation`.
- The `Order Service` calls the `Inventory Service` Feign client `/inventory/release` to move stock from `quantityReserved` back to `quantityAvailable`, and marks the order `CANCELLED`.
- **Orphan Cleanup (Expiry):** If the user abandons the page, the `OrderExpirationScheduler` background thread sweeps `RESERVED` orders older than 2 minutes every 10 minutes, automatically releasing stock via Feign and marking the orders as `EXPIRED`.

---

## 5. Real-Time STOMP WebSockets

### Decision
Notifications are pushed to clients using a dedicated WebSocket connection broker in the `Notification Service` on port `8086`.

### Implementation
- **Broker Config:** `WebSocketConfig.java` enables a STOMP broker mapped to path `/ws` using SockJS fallback options.
- **Subscription Model:** Clients subscribe to dynamic user channels: `/topic/notifications/{userId}`.
- **Stateful History:** Unlike typical simple chat gateways, the notification service persists alerts in `notification_db` using a relational `notifications` table. This allows users to retrieve historical alerts via `GET /notifications/user/{userId}` even if they were offline during the WebSocket broadcast.

### Trade-offs
- **Memory Consumption:** Maintaining active SockJS connections requires persistent TCP sockets, placing a memory footprint on the `Notification Service`.
- **Routing Isolation:** The API Gateway must bypass standard REST filters and allow WebSocket handshakes to pass directly to port `8086` without breaking connection contexts.

---

## 6. Security Model: Gateway-Trust & Decryption Bypass

Decrypting JWT claims on every single downstream microservice creates a heavy CPU and configuration overhead.

### Decision
The platform implements a **Gateway-Trust Pattern** to centralize authentication at the API Gateway (`:8080`).

### Implementation
- **Gateway Signature Validation:** The API Gateway verifies the client's JWT signature using the public RSA key fetched on startup from `/auth/public-key`.
- **Header Injection:** The gateway strips client-supplied verification headers, then injects secure upstream headers:
  - `X-User-Id`: Extracted user principal UUID.
  - `X-User-Email`: Extracted user email address.
  - `X-User-Roles`: Comma-separated user authority scopes.
  - `X-Gateway-Secret`: A protected shared signature config key.
- **Downstream GatewayAuthFilter:** Downstream services use a lightweight filter that validates the presence and correctness of `X-Gateway-Secret`. If correct, the request is trusted, and the local Spring `SecurityContextHolder` is immediately hydrated using the values from `X-User-Id` and `X-User-Roles`.

### Trade-offs
- **Internal Secret Security:** The security of the downstream services relies entirely on keeping `X-Gateway-Secret` secure. If an attacker manages to execute requests directly inside the internal network while spoofing this header, they can bypass downstream auth filters. Downstream ports must remain blocked from direct public access.
