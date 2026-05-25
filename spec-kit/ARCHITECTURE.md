# Architecture Overview — Microservices Platform

This document describes the high-traffic microservices-oriented architecture implemented for the E-Commerce platform, including service responsibilities, network ports, inter-service communication protocols, database topologies, and security configurations.

---

## 🏗️ System Architecture Map

The system is designed with a decentralized database-per-service architecture. All client interactions pass through the API Gateway, which handles authentication and routes requests downstream.

```
                             ┌───────────────────────┐
                             │    Browser/Client     │
                             │  Angular App (:4200)  │
                             └───────────┬───────────┘
                                         │ HTTPS / WS
                                         ▼
                             ┌───────────────────────┐
                             │   API Gateway :8080   │
                             │                       │
                             │  - JwtAuthFilter      │
                             │  - RateLimitingFilter │
                             │  - CorsConfig         │
                             └───────────┬───────────┘
                                         │ Internal routing (StripPrefix=1)
        ┌────────────────────────────────┼────────────────────────────────┐
        │ (:8081)                        │ (:8082)                        │ (:8083)
┌───────▼───────┐                ┌───────▼───────┐                ┌───────▼───────┐
│ Auth Service  │                │ Product Srv.  │                │Inventory Srv. │
│               │                │               │                │               │
│ - BCrypt/JWT  │                │ - Caffeine    │                │ - Stock Resv. │
└───────┬───────┘                └───────┬───────┘                └───────┬───────┘
        │                                │                                │
┌───────▼───────┐                ┌───────▼───────┐                ┌───────▼───────┐
│    auth_db    │                │  product_db   │                │ inventory_db  │
└───────────────┘                └───────────────┘                └───────────────┘

        │ (:8087)                        │ (:8084)                        │ (:8085)
┌───────▼───────┐                ┌───────▼───────┐                ┌───────▼───────┐
│ Cart Service  │                │ Order Service │                │ Payment Srv.  │
│               │                │               │                │               │
│ - persistent  │                │ - Two-Phase   │                │ - Stripe / CB │
└───────┬───────┘                └───────┬───────┘                └───────┬───────┘
        │                                │                                │
┌───────▼───────┐                ┌───────▼───────┐                ┌───────▼───────┐
│    cart_db    │                │   order_db    │                │  payment_db   │
└───────────────┘                └───────────────┘                └───────────────┘

        │ (:8086)                                                         │ (:8761)
┌───────▼───────┐                                                 ┌───────▼───────┐
│ Notification  │◄───────────────── Feign / REST ─────────────────┤ Eureka Server │
│ Service (WS)  │                                                 │               │
└───────┬───────┘                                                 │ - Registry    │
        │                                                         └───────────────┘
┌───────▼───────┐
│notification_db│
└───────────────┘
```

---

## ⚙️ Service Catalog & Port Matrix

The application comprises nine individual services running on specific local ports.

| Service Name | Port | Database | Primary Technical Responsibilities |
| :--- | :--- | :--- | :--- |
| **Eureka Server** | `8761` | *None* | Central service discovery registry. All microservices register dynamically on startup. |
| **API Gateway** | `8080` | *None* | Single entry point. Handles reactive routing, asymmetric JWT validation, rate limiting, and CORS. |
| **Auth Service** | `8081` | `auth_db` | Standardizes user registration, password hashing (BCrypt), JWT issuance (access + refresh tokens), and hosts the dynamic public key endpoint. |
| **Product Service** | `8082` | `product_db` | Handles the product catalog, categorizations, custom homepage banners, search/filter indexes, and Caffeine in-memory caching. |
| **Inventory Service** | `8083` | `inventory_db` | Tracks stock levels, manages reservations/releases, exposes low-stock metrics, and records audit trails in a stock movement ledger. |
| **Order Service** | `8084` | `order_db` | Handles cart checkout, two-phase order reservations, status transitions, and schedules expired reservation cleanups. |
| **Payment Service** | `8085` | `payment_db` | Integrates with Stripe (Checkout Sessions, session retrieval, secure webhooks), simulates offline mock payments, and manages retries. |
| **Notification Service**| `8086` | `notification_db`| Manages STOMP WebSocket server (`/ws`), stores persistent in-app notifications, and handles dynamic read status tracking. |
| **Cart Service** | `8087` | `cart_db` | Persists shopping carts in PostgreSQL using the user's gateway-forwarded email address. |

> [!NOTE]
> **Port Deviations from Original Plan:**
> The original specification plan outlined the Order Service on port `8085`, Payment Service on `8086`, and Notification Service on `8087`. The implemented services were adjusted in their configurations (`application.yml`) to resolve local port availability conflicts, assigning Order Service to `8084`, Payment Service to `8085`, Notification Service to `8086`, and Cart Service to `8087`.

---

## 🔌 Inter-Service Communication

Services interact using three complementary communication models:

### 1. Synchronous REST with OpenFeign
For workflows requiring instant execution confirmation, services call each other directly using Spring Cloud OpenFeign.
- **Service Discovery Integration:** Rather than hardcoding direct physical endpoints, `@FeignClient` definitions use Eureka-registered service names (e.g., `@FeignClient(name = "inventory-service")`). Calls are load-balanced on the client side using Spring Cloud LoadBalancer.
- **Internal Security Propagation:** Requests made via Feign include a custom `FeignConfig` interceptor. This automatically propagates the `X-Gateway-Secret` header (proving the request is internal) and passes through the active caller's identity headers (`X-User-Id`, `X-User-Roles`).
- **Resilience integration:** Calls to external microservices are guarded by Resilience4j circuit breakers to guarantee graceful degradation if a service fails.

### 2. Real-Time STOMP WebSockets
The Notification Service exposes a reactive WebSocket broker on port `8086` (path `/ws`).
- **Subscription Channel:** Clients establish a SockJS STOMP connection and subscribe to private user topics: `/topic/notifications/{userId}`.
- **Asynchronous Broadcasting:** When an order is reserved, confirmed, or shipped, the Order Service initiates a lightweight REST request to `/notifications/send` on the Notification Service. The Notification Service persists the notification in `notification_db` and immediately broadcasts the payload down the user's active WebSocket channel.

---

## 🔒 Security Model (Gateway-Trust & JWT Pattern)

The microservices follow the **Gateway-Trust Architecture Pattern**, avoiding redundant security decoders in individual downstream applications.

```
Client Request ──► [ API Gateway :8080 ] ──(Verifies JWT RS256 signature)──┐
                                                                          │ Injects Headers:
                                                                          │ - X-User-Id
                                                                          │ - X-User-Email
                                                                          │ - X-User-Roles
                                                                          │ - X-Gateway-Secret
                                                                          ▼
                                                       [ Downstream Service (e.g., :8082) ]
                                                       (GatewayAuthFilter checks X-Gateway-Secret,
                                                        re-populates SecurityContextHolder)
```

### 1. API Gateway Edge Authentication
- The API Gateway maintains a `JwtAuthenticationFilter` that intercepts all inbound HTTP traffic.
- **Asymmetric Validation:** The gateway loads a public RSA key dynamically from the Auth Service (`/auth/public-key`) on startup. It uses this key to validate the asymmetric **RS256** signatures of client bearer tokens, checking for expiry and structure.
- **Header Injection:** On successful validation, the gateway extracts JWT claims and injects secure request headers before forwarding:
  - `X-User-Id`: Extracted from JWT subject (UUID).
  - `X-User-Email`: Extracted from email claim.
  - `X-User-Roles`: Extracted from roles claim (comma-separated).
  - `X-Gateway-Secret`: A protected environment token proving the gateway authorized the request.

### 2. Downstream Gateway-Trust Filtering
- Downstream services (Products, Inventory, Orders, Payments, Notifications, Cart) do not import cryptographic libraries or decrypt JWT signatures.
- Instead, each microservice implements a `GatewayAuthFilter` (`OncePerRequestFilter`).
- **Internal Validation:** The filter inspects the incoming `X-Gateway-Secret` header. If it is missing or does not match the service's configured key, the request is rejected with `403 Forbidden` (preventing bypass attacks where clients call downstream ports directly).
- **Security Context Hydration:** If the secret matches, the filter extracts `X-User-Id` and `X-User-Roles` and hydrates the local Spring Security `SecurityContextHolder` with a pre-authenticated `UsernamePasswordAuthenticationToken` containing the user's ID as the principal and their mapped roles.

---

## 💾 Database Strategy

The platform enforces a strict **Database-per-Service** strategy to avoid schema coupling and support independent scalability.

- **Isolation:** No microservice is permitted to query the database table of another microservice. All data synchronization is performed through HTTP REST or internal Feign calls.
- **Migrations:** Database schemas are versioned and migrated systematically using **Flyway**. Migration scripts are located under `src/main/resources/db/migration/` inside each service.
- **Schema Distribution:**
  - `auth_db`: Stores users, passwords (BCrypt strength 12), and role assignments.
  - `product_db`: Stores catalog categories, product items, images, homepage banners, and promotional discount codes.
  - `inventory_db`: Tracks available/reserved quantities, stock thresholds, and records all historical transactions in `stock_movements`.
  - `cart_db`: Houses active and persistent user shopping carts.
  - `order_db`: Stores order headers, denormalized items, and customer addresses.
  - `payment_db`: Logs Stripe checkout sessions and payment transaction outcomes.
  - `notification_db`: Records a ledger of user notifications and read statuses.
