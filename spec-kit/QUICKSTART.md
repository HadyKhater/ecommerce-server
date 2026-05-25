# Quick Start: E-Commerce Platform Spec-Kit

## Overview

This quick start guide gets you oriented with the Spec-Kit project structure in 5 minutes.

---

## What is Spec-Kit?

Spec-Kit is a comprehensive product specification and architecture documentation for a high-traffic, microservices-based e-commerce platform built with Spring Boot 3.5.14 and Java 21.

---

## The Five Steps

Each folder represents a step in the development process:

### 1. **Constitution** (`docs/constitution.md`)
   - Read first
   - Establishes governing principles
   - Architecture standards
   - Code quality guidelines
   - Security policies
   - **When to use:** Kick-off meetings, onboarding new developers

### 2. **Specify** (`specify/product-spec.md`)
   - Feature definitions
   - User stories
   - Business requirements
   - API contracts
   - **When to use:** Requirements gathering, product planning

### 3. **Plan** (`plan/architecture-plan.md`)
   - System architecture
   - Service topology
   - Technology stack
   - Database strategy
   - Inter-service communication
   - **When to use:** Architecture design review, deployment planning

### 4. **Tasks** (`tasks/tasks.md`)
   - Development tasks
   - Acceptance criteria
   - Implementation steps
   - Dependencies
   - **When to use:** Sprint planning, task assignment

### 5. **Implement** (`implement/implementation-guide.md`)
   - How to write code
   - Common patterns
   - Testing approach
   - Troubleshooting
   - **When to use:** Daily development, code reviews

---

## Quick Navigation

### For Product Managers
1. Read: `constitution.md` (Principles)
2. Read: `product-spec.md` (Features)
3. Review: `architecture-plan.md` (Services)

### For Architects
1. Read: `constitution.md` (Standards)
2. Review: `architecture-plan.md` (Complete architecture)
3. Reference: `plan/architecture-plan.md` (Trade-offs)

### For Developers
1. Skim: `constitution.md` (Code standards)
2. Skim: `product-spec.md` (Requirements)
3. Study: `architecture-plan.md` (Services you'll work on)
4. Reference: `tasks/tasks.md` (Your sprint)
5. Use daily: `implementation-guide.md` (Patterns & setup)

### For QA/Testing
1. Read: `product-spec.md` (Features to test)
2. Read: `tasks/tasks.md` (Acceptance criteria)
3. Reference: `implementation-guide.md` (Testing section)

---

## Key Services Quick Reference

```
┌─────────────────────────────────────────────────────────┐
│              Angular Frontend :4200                     │
└────────────────────┬────────────────────────────────────┘
                     │ HTTPS / WebSocket
        ┌────────────▼──────────────┐
        │     API Gateway :8080     │← All traffic goes here
        │  - JWT verification (RS256)│
        │  - Header forwarding       │
        │  - Request routing         │
        └────────────┬──────────────┘
                     │
    ┌────────────────┼───────────────────┐
    ▼                ▼                   ▼
Auth :8081     Product :8082       Inventory :8083
- Register     - Products           - Stock levels
- Login        - Categories         - Reservations
- JWT tokens   - Caffeine caching   - Audited ledger

Order :8084    Payment :8085        Notification :8086
- Place orders  - Stripe payments    - WebSockets broker
- View history  - Webhook listener   - Persist history
- Two-Phase flow - Retry / CB        - ws://localhost:8086/ws

Cart :8087     Eureka :8761
- Persist items - Service registry
- Caffeine     - Health checks
```

> **Security Model:** Gateway verifies JWT RS256 and injects `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Gateway-Secret` headers. Services validate `X-Gateway-Secret` — direct access without it returns 403.

---

## Services at a Glance

| Service | Port | Database | Purpose | Status |
|---------|------|----------|---------|--------|
| Eureka | 8761 | (registry) | Service discovery | ✅ Complete |
| Gateway | 8080 | (none) | API routing + JWT RS256 | ✅ Complete |
| Auth | 8081 | auth_db | Users, tokens, logout | ✅ Complete |
| Product | 8082 | product_db | Catalog, search, categories | ✅ Complete |
| Inventory | 8083 | inventory_db | Stock, reserve, restock, movement history | ✅ Complete |
| Order | 8084 | order_db | Two-phase checkout flow & lifecycle | ✅ Complete |
| Payment | 8085 | payment_db | Stripe payments + simulated retry | ✅ Complete |
| Notification | 8086 | notification_db | WebSocket broker + persistent alerts | ✅ Complete |
| Cart | 8087 | cart_db | Persistent cart with Caffeine caching | ✅ Complete |

---

## Startup Order

Start services in this order:

1. **PostgreSQL** (all databases must exist: `auth_db`, `product_db`, `inventory_db`, `cart_db`, `order_db`, `payment_db`, `notification_db`)
2. **Eureka Server** (port 8761) - Services depend on this
3. **Auth Service** (port 8081) - Gateway needs this for public key
4. **API Gateway** (port 8080) - Fetches public key from auth-service on startup
5. **Product Service** (port 8082)
6. **Inventory Service** (port 8083)
7. **Order Service** (port 8084)
8. **Payment Service** (port 8085)
9. **Notification Service** (port 8086)
10. **Cart Service** (port 8087)
11. **Angular Frontend** (port 4200): `npm run dev` (running Vite dev server)

### Environment Variables Required

```bash
# API Gateway
gateway.jwt.public-key=<RSA public key>  # Or fetched from auth-service
gateway.internal.secret=super-secret-gateway-key

# All Services
GATEWAY_INTERNAL_SECRET=super-secret-gateway-key
DB_PASSWORD=<your_postgres_password>

# Auth Service
JWT_PRIVATE_KEY_PATH=keys/auth-keypair.pem
```

---

## 30-Second Example: Place an Order

### 1. Frontend calls API Gateway

```bash
POST /api/orders/reserve HTTP/1.1
Authorization: Bearer {jwt_token}

{ "items": [...], "shippingAddress": "..." }
```

### 2. Gateway validates JWT, extracts claims, injects headers, routes to Order Service `:8084`

### 3. Order Service:
   - Calls Inventory Service `:8083` via Feign to reserve stock (guarded by `@Version` lock)
   - Returns temporary reservation order (status `RESERVED`)
   - Client pays using Stripe session or Mock processor on Payment Service `:8085`
   - Payment success webhook triggers `/orders/{id}/confirm` on Order Service
   - Order Service changes status to `CONFIRMED` and dispatches notification REST call to Notification Service `:8086`

### 4. Notification Service:
   - Persists notification in `notification_db`
   - Broadcasts payload down the client's STOMP WebSocket subscriber topic `/topic/notifications/{userId}`

### 5. Frontend receives order confirmation alert in real-time

---

## Key Architecture Decisions

### Why Optimistic Locking for Inventory & Cart?

- **Better throughput** in high-traffic scenarios
- **No deadlocks** under concurrent load
- **Automatic retry** in service layer
- Proven pattern for e-commerce platforms

### Why WebSocket Broadcasts for Notifications?

- **Real-time push delivery** to client UI
- **Online/Offline support** via relational persistence in `notification_db`
- **Low latency** compared to polling models

### Why Database Per Service?

- **Independent scaling** = each service controls its database
- **Loose coupling** = services don't share data schema
- **Deployment flexibility** = can upgrade database per service independently

---

## Important Constraints

❌ **NOT using:**
- Docker (local dev only)
- Redis (use Caffeine Cache)
- RabbitMQ (use ApplicationEventPublisher)
- Load balancers (single instance per service)

✅ **Using:**
- Spring Boot + Java 21
- PostgreSQL
- HikariCP connection pooling
- Caffeine for caching
- Flyway for migrations
- OpenFeign for inter-service calls
- Resilience4j for circuit breakers

---

## Getting Started Checklist

- [ ] Read `constitution.md` (20 min)
- [ ] Understand the 5 phases (5 min)
- [ ] Review service architecture in `architecture-plan.md` (15 min)
- [ ] Follow setup instructions in `implementation-guide.md` (30 min)
- [ ] Run first service and verify on Eureka (10 min)

**Total time:** ~90 minutes to understand the full system

---

## Common Questions

**Q: Why 9 services for an e-commerce platform?**  
A: Each service has a single responsibility. Separation allows independent deployment and scaling.

**Q: Why store cart in database instead of Redis?**  
A: PostgreSQL provides sufficient performance. In production, could add Redis for caching layer.

**Q: How do we prevent overselling?**  
A: Optimistic locking on inventory with automatic retry. See concurrency test in Tasks.

**Q: What if inventory service is down?**  
A: Circuit breaker opens after N failures, fails fast. User sees "Service temporarily unavailable".

**Q: How are passwords stored?**  
A: BCrypt with strength 12. Never plain text, never reversible.

**Q: Can customers see other users' orders?**  
A: No. Order Service checks authorization: users see only their orders, admins see all.

---

## Where to Go Next

1. **Starting development?** → Read `implementation-guide.md`
2. **Assigning tasks?** → Check `tasks/tasks.md`
3. **In a meeting, need to explain architecture?** → Show diagrams in `architecture-plan.md`
4. **Debugging code issue?** → Troubleshooting section in `implementation-guide.md`
5. **Need to add new feature?** → Start with `product-spec.md`, then update architecture

---

## Document Glossary

- **Optimistic Locking:** Database technique using version field to detect concurrent modifications
- **Circuit Breaker:** Pattern to fail fast if downstream service is unavailable
- **Idempotency:** Property where repeated requests have same effect as single request
- **Eventual Consistency:** Guarantee that data will eventually be consistent across services
- **Domain Event:** Business event published by a service for other services to listen to
- **DTO:** Data Transfer Object, lightweight record used for API contracts
- **Repository:** Data access object pattern for database queries
- **Feign Client:** HTTP client using service discovery for inter-service calls

---

**Version:** 1.0  
**Last Updated:** April 2026  
**Status:** Ready for implementation

