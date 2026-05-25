# Feature Implementation Analysis — Implemented vs. Required

This document provides a comprehensive mapping of the core features and non-functional requirements outlined in the project brief against the actual technical implementation found in the codebase.

---

## 📊 Summary of Feature Progress

| Feature Requirement | Status | Technical Components involved | Description |
| :--- | :---: | :--- | :--- |
| **1. Product Catalog** | ✅ | `ProductController`, `CategoryController`, `ProductServiceImpl`, `BannerController`, `Caffeine` | Search, filter, pagination, category management, homepage banners, and in-memory catalog caching. |
| **2. User Management** | ✅ | `AuthController`, `TokenProvider`, `TokenBlacklistService`, `GatewayAuthFilter` | RS256 dynamic JWT keys, BCrypt hashing, profile lookup, and session blacklisting upon logout. |
| **3. Shopping Cart** | ✅ | `CartController`, `CartServiceImpl`, `@Version` | Persistent cart stored in `cart_db`, associated via gateway-forwarded email, protected by optimistic locking. |
| **4. Order Processing** | ✅ | `OrderController`, `OrderServiceImpl`, `OrderExpirationScheduler` | Complete lifecycle managed via custom Two-Phase checkout flow. Features auto-expiry of reserved stock. |
| **5. Payment Simulation** | ✅ | `StripeController`, `StripeServiceImpl`, `PaymentController`, `Resilience4j` | Dual-mode: Full Stripe checkout session creation + webhook listener, alongside mock provider with exponential backoff retries. |
| **6. Inventory Management** | ✅ | `InventoryController`, `InventoryServiceImpl`, `@Version`, `StockMovement` | Stock reservations, restocks, low-stock metrics, movements ledger, and concurrent-safe optimistic locks. |
| **7. Notifications** | ✅ | `NotificationController`, `WebSocketConfig`, `SockJS`, `STOMP` | Persistent DB-stored notifications paired with active WebSocket push broker broadcasting to clients in real-time. |

---

## 🔍 Detailed Feature Walkthrough

### 1. Product Catalog
* **Required:** Search, filter, pagination.
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **Search & Filters:** Implemented in `ProductController.getProducts()`. Supports search by keyword (matches name and description), filter by category UUID, minimum price, maximum price, and active/inactive status.
  * **Pagination:** Enforces default paging (25 items) using Spring Data `Pageable` parameters (`page`, `size`), mapping database queries to a custom `PagedResponse<ProductDTO>` record.
  * **Caching:** Implemented inside `ProductServiceImpl` using **Caffeine** in-memory cache. Entries are cached by UUID with a 15-minute TTL, and are automatically invalidated upon any administrative product update or deletion.
  * **Banners & Promotions:** Dynamically manages promotional codes (`PromotionController`) and homepage landing sliders (`BannerController`).

### 2. User Management
* **Required:** JWT authentication, user profiles, address management.
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **Asymmetric RS256 Auth:** Generates 2048-bit RSA asymmetric key pairs. The `TokenProvider` signs JWT access tokens (15-minute expiry) and refresh tokens (7-day expiry) using the private key, while the API Gateway dynamically verifies signatures using the exposed `/auth/public-key` endpoint.
  * **Profile Management:** Users retrieve profiles using `/auth/me`, where the controller maps active identities via the injected `X-User-Email` header.
  * **Secure Logout:** The `TokenBlacklistService` blacklists active access tokens in memory until their original expiration times, rejecting reuse.
  * **Password Protection:** Hashes registration passwords using BCrypt with a workload strength of 12.

### 3. Shopping Cart
* **Required:** Persistent, concurrent-safe cart.
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **PostgreSQL Persistence:** Shopping carts are stored in a dedicated `cart_db` database using `carts` and `cart_items` relational tables. Carts persist across user sessions.
  * **Gateway Identity Mapping:** The cart is linked to the user's account using the `X-User-Email` header injected by the API Gateway during downstream routing.
  * **Concurrent Safe Mod:** Uses a `@Version` locking field on the `Cart` entity. If a user modifies their cart on multiple devices concurrently, Hibernate blocks conflicting updates and throws an `OptimisticLockingFailureException`, preventing total item count corruption.

### 4. Order Processing
* **Required:** Lifecycle (Pending → Paid → Shipped → Delivered).
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **Order Lifecycle:** Tracks orders using six distinct states: `RESERVED`, `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, and `EXPIRED`.
  * **Two-Phase checkout reservation flow:** 
    - *Phase 1 (Reservation):* `POST /orders/reserve` reserves stock via Inventory Service and creates a `RESERVED` order.
    - *Phase 2 (Confirmation):* Payment success triggers `PUT /orders/{id}/confirm` which updates the status to `CONFIRMED` and finalizes stock.
    - *Phase 2 (Cancel Reservation):* Payment failure or timeout triggers `PUT /orders/{id}/cancel-reservation`, releasing the reserved stock.
  * **Auto-Expiration Scheduler:** An active `OrderExpirationScheduler` executes every 10 minutes. It queries all `RESERVED` orders older than 2 minutes, updates their statuses to `EXPIRED`, automatically calls the Inventory Service Feign client to release stock, and triggers a real-time notification to the client.

### 5. Payment Simulation & Stripe Integration
* **Required:** Mock provider, retries, idempotency.
* **Implemented:** ✅ **FULLY IMPLEMENTED** (Built beyond requirements)
  * **Stripe Checkout Sessions:** Rather than a simple mock, a fully functional Stripe integration is built under `StripeController`. It creates secure Stripe Checkout sessions (`/stripe/checkout`), returns direct Stripe payment URLs to the frontend, and handles session retrieval (`/stripe/session/{id}`).
  * **Stripe Webhook Listener:** The `/stripe/webhook` endpoint validates secure Stripe signatures (`Stripe-Signature`), parses event payloads, and automatically confirms matching orders via Order Service Feign clients when a checkout session reports success.
  * **Simulated Mock Payments:** Houses an offline simulation engine `/payments/process`. The mock payment processor simulates a real bank gateway with an ~80% success rate, throwing connection errors to validate resilience.
  * **Idempotency:** Implemented on mock payments using the `orderId` as a unique transaction key. Duplicate charge requests for the same order are intercepted, returning the existing payment transaction payload.
  * **Resilience4j Retries:** Downstream mock gateway failures are automatically retried using exponential backoff (up to 3 times, starting with a 1-second delay and a 2x multiplier).

### 6. Inventory Management
* **Required:** Prevent overselling, race condition handling.
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **Overselling Prevention:** Tracks stock using separate `quantity_available` and `quantity_reserved` columns.
  * **Optimistic Locking:** The `Inventory` entity utilizes a JPA `@Version` property mapping. High-concurrency operations (such as parallel stock reservations) verify that stock is not overwritten between check and write, throwing conflict exceptions to block overselling.
  * **Ledger Auditing:** Changes to stock trigger a transaction record in the `stock_movements` database, categorizing movements (e.g., `RESERVE`, `RELEASE`, `RESTOCK`, `ADJUST`) alongside detailed reason descriptions.

### 7. Notifications
* **Required:** Order confirmation, status updates.
* **Implemented:** ✅ **FULLY IMPLEMENTED**
  * **Persistent DB Notifications:** Notifications are stored in `notification_db` (`notifications` table) containing user UUIDs, titles, bodies, and read status flags.
  * **STOMP WebSockets:** Implements `EnableWebSocketMessageBroker` registering endpoint `/ws` with SockJS support. Pushes updates directly to active users subscribed to `/topic/notifications/{userId}`.
  * **Feign Broker Dispatch:** The Order Service and Payment Service dispatch notifications by calling `notificationClient.sendNotification()`. The Notification Service handles database insertion and immediately broadcasts the payload down the client's WebSocket channel.

---

## 🛡️ Non-Functional Requirements Verification

### 🚀 Scalability & High Throughput
- **Database Per Service:** Bypasses central bottleneck databases. Each service scales its connection pool independent of the platform.
- **Optimistic Locking:** Avoids pessimistic locks (`SELECT FOR UPDATE`), which lock database rows and block concurrent reads. This ensures exceptional performance under high cart modification volumes.

### 🔌 Fault Tolerance & Circuit Breakers
- **Resilience4j Circuit Breaker:** Downstream calls in `order-service` to `inventory-service` and `product-service` are wrapped in circuit breakers. Under service blackouts, the breaker opens, rejecting calls immediately to avoid cascading resource exhaustion.

### 🛡️ Edge Security & Input Validation
- **Centralized Edge Verification:** Edge JWT checks prevent public traffic from reaching downstream service logic without validation.
- **Bean Validation (Hibernate Validator):** Request bodies (`CreateProductRequest`, `RegisterRequest`, `AddToCartRequest`) utilize standard annotations (`@NotNull`, `@Size`, `@Min`, `@Email`). Invalid JSON requests are blocked by Spring framework before executing any service code.
