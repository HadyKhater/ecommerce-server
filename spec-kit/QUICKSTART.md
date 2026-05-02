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
│                    CLIENT / BROWSER                      │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────▼──────────────┐
        │     API Gateway :8080     │← All traffic goes here
        │  - JWT validation         │
        │  - Request routing        │
        └────────────┬──────────────┘
                     │
    ┌────────────────┼───────────────────┐
    ▼                ▼                   ▼
Auth         Product & Inventory    Order & Payment
:8081        :8082, :8083          :8085, :8086

Cart         Notification          Eureka
:8084        :8087                 :8761
```

---

## Services at a Glance

| Service | Port | Database | Purpose |
|---------|------|----------|---------|
| Eureka | 8761 | (registry) | Service discovery |
| Gateway | 8080 | (none) | API routing + JWT |
| Auth | 8081 | auth_db | Users, tokens |
| Product | 8082 | product_db | Catalog, search |
| Inventory | 8083 | inventory_db | Stock (with optimistic lock) |
| Cart | 8084 | cart_db | Shopping cart |
| Order | 8085 | order_db | Order lifecycle |
| Payment | 8086 | payment_db | Mock payment processor |
| Notification | 8087 | (none) | Event-driven emails |

---

## 30-Second Example: Place an Order

### 1. Frontend calls API Gateway

```bash
POST /api/orders HTTP/1.1
Authorization: Bearer {jwt_token}

{ "cartItems": [...], "address": {...} }
```

### 2. Gateway validates JWT, routes to Order Service `:8085`

### 3. Order Service:
   - Calls Inventory Service `:8083` to reserve stock (optimistic lock)
   - Calls Payment Service `:8086` to process payment (with retry)
   - Creates Order in database
   - Publishes `OrderPaidEvent`

### 4. Order Service returns `OrderResponse` to Gateway

### 5. Notification Service (listening to events):
   - Receives `OrderPaidEvent`
   - Sends mock email: "Order Confirmed"

### 6. Frontend receives order confirmation

---

## Key Architecture Decisions

### Why Optimistic Locking for Inventory?

- **Better throughput** in high-traffic scenarios
- **No deadlocks** under concurrent load
- **Automatic retry** in service layer (transparent to caller)
- Proven pattern for e-commerce platforms

### Why Spring ApplicationEventPublisher for Notifications?

- **In-process events** = <1ms latency
- **No message broker overhead** = simpler dev environment
- **Transactional consistency** = events published in same transaction as order creation
- **Eventual consistency is fine** for notifications (1-5 second delay acceptable)

### Why Database Per Service?

- **Independent scaling** = each service controls its database
- **Loose coupling** = services don't share data
- **Deployment flexibility** = can upgrade database per service
- **Data consistency trade-off** = accept eventual consistency for reads across services

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

