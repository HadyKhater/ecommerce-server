# E-Commerce Platform Spec-Kit

**A comprehensive specification and architecture documentation for a high-traffic microservices e-commerce platform.**

---

## 📋 Document Structure

This Spec-Kit is organized into 5 strategic phases:

### Phase 1: Constitution (`docs/constitution.md`)
**Establishes governing principles**
- Architecture standards (Spring Boot, Java 21, microservices)
- Code quality and design patterns
- Security protocols (JWT RS256, BCrypt)
- Data consistency model (optimistic locking, eventual consistency)
- Testing standards (unit, integration, concurrency)

### Phase 2: Specify (`specify/product-spec.md`)
**Defines all system features**
- Product Catalog (search, filter, pagination)
- User Management (registration, authentication, profiles)
- Shopping Cart (persistence, concurrent modifications)
- Order Processing (lifecycle, cancellation, history)
- Payment Processing (mock provider, retry logic, idempotency)
- Inventory Management (stock tracking, reservations, overselling prevention)
- Notifications (mock email, event-driven)

### Phase 3: Plan (`plan/architecture-plan.md`)
**Outlines complete system architecture**
- Service topology (9 microservices, ports 8080-8087)
- Technology stack (PostgreSQL, Caffeine, OpenFeign, Resilience4j)
- Inter-service communication patterns (Feign + Circuit Breaker, Application Events)
- Database strategy (per-service databases, Flyway migrations)
- Trade-off decisions and rationale

### Phase 4: Tasks (`tasks/tasks.md`)
**Specifies concrete development tasks**
- 50+ detailed tasks with acceptance criteria
- Dependencies and implementation steps
- Foundation, service-specific, testing, and documentation phases
- Estimated timelines and effort allocation

### Phase 5: Implement (`implement/implementation-guide.md`)
**Provides practical implementation guidance**
- Local development environment setup
- Common design patterns (services, DTOs, optimistic locking, idempotency)
- Testing approach (unit, integration, concurrency)
- Running services locally
- Troubleshooting guide
- Development workflow

### Supplementary: Quick Start (`docs/QUICKSTART.md`)
**Gets oriented in 5 minutes**
- 30-second example: placing an order
- Key services reference table
- Common questions answered
- Entry points for different roles

---

## 🎯 Quick Navigation by Role

### Product Manager
Start here: `docs/QUICKSTART.md` (5 min) → `specify/product-spec.md` (30 min) → `plan/architecture-plan.md` (Skim for services)

### Solution Architect
Start here: `docs/constitution.md` (30 min) → `plan/architecture-plan.md` (60 min) → Full review of trade-offs

### Development Team Lead
Start here: `docs/QUICKSTART.md` (5 min) → `tasks/tasks.md` (plan sprints) → `implement/implementation-guide.md` (reference)

### Backend Developer
Start here: `implement/implementation-guide.md` (Part 1-2, setup + patterns) → `tasks/tasks.md` (your sprint) → Return to implementation-guide.md for reference

### QA/Tester
Start here: `specify/product-spec.md` (understand features) → `tasks/tasks.md` (acceptance criteria) → `implement/implementation-guide.md` (testing section)

---

## 🚦 Current Implementation Status (May 2026)

### ✅ Complete (Ready for Use)

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| **Eureka Server** | 8761 | ✅ Complete | Service discovery operational |
| **API Gateway** | 8080 | ✅ Complete | JWT validation, routing, rate limiting |
| **Auth Service** | 8081 | ✅ Complete | Full auth flow: register, login, refresh, logout |
| **Product Service** | 8082 | ✅ Complete | CRUD, categories, search, caching |

### ⚠️ Placeholder (Application Only)

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| **Order Service** | 8085 | ⚠️ Placeholder | Feign clients only, no order logic |
| **Inventory Service** | 8083 | ⚠️ Placeholder | Health check only, no inventory logic |
| **Payment Service** | 8086 | ⚠️ Placeholder | Application class only |
| **Notification Service** | 8087 | ⚠️ Placeholder | Application class only |

### ❌ Missing

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| **Cart Service** | 8084 | ❌ Missing | Folder doesn't exist |

### 📊 Overall Progress: 33% (17/51 tasks)

**Ready for frontend integration:** Auth, Product
**Blocking order flow:** Cart, Inventory, Payment

---

## 🏗️ System Architecture at a Glance

```
                         Browser/Client
                              │
                ┌─────────────▼─────────────┐
                │    API Gateway :8080      │
                │ (JWT validation + routing)│
                └─────────────┬─────────────┘
                              │
        ┌─────────────────────┼──────────────────────┐
        │                     │                      │
        ▼                     ▼                      ▼
   ┌────────────┐      ┌────────────┐       ┌─────────────┐
   │Auth Service│      │Product Srv │       │Inventory Srv│
   │   :8081    │      │   :8082    │       │   :8083     │
   └────────────┘      └────────────┘       └─────────────┘
        │                     │                      │
        ▼                     ▼                      ▼
    auth_db          product_db               inventory_db
    (Users)          (Catalog)          (Stock w/ @Version)

        ┌────────────────┬──────────────────┐
        ▼                ▼                  ▼
   ┌────────────┐  ┌───────────┐    ┌──────────────┐
   │Cart Service│  │Order Srv  │    │Payment Srv   │
   │  :8084     │  │  :8085    │    │  :8086       │
   └────────────┘  └───────────┘    └──────────────┘
        │               │                  │
        ▼               ▼                  ▼
    cart_db         order_db          payment_db
```

**Additional Services:**
- **Eureka Server** `:8761` - Service discovery registry
- **Notification Service** `:8087` - Event listener (stateless, no DB)

---

## 🔑 Key Technical Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| **Locking Strategy** | Optimistic (with @Version) | Better throughput, no deadlocks in high traffic |
| **Caching** | Caffeine (in-memory) | No Redis needed, suitable for dev environment |
| **Async Events** | ApplicationEventPublisher | In-process, no broker needed, cost-effective |
| **Database** | PostgreSQL (per service) | Reliable, standard SQL, independent scaling |
| **Authentication** | JWT RS256 | Stateless, scalable, asymmetric key security |
| **Inter-Service Calls** | OpenFeign | Service discovery integration, clean API |
| **Resilience** | Resilience4j | Circuit breaker, retry, timeout patterns |

---

## 📊 Service Summary

| Service | Port | DB | Purpose | Key Responsibility |
|---------|------|----|---------|--------------------|
| **Eureka** | 8761 | - | Service Discovery | Register/discover services |
| **Gateway** | 8080 | - | API Entry Point | JWT validation, routing |
| **Auth** | 8081 | auth_db | Authentication | JWT generation, user management |
| **Product** | 8082 | product_db | Catalog | Search, filter, caching |
| **Inventory** | 8083 | inventory_db | Stock Tracking | Reservations, overselling prevention |
| **Cart** | 8084 | cart_db | Shopping Cart | Persistent cart, concurrent safety |
| **Order** | 8085 | order_db | Order Lifecycle | Create, track, cancel orders |
| **Payment** | 8086 | payment_db | Payment Processing | Mock payment, retry, idempotency |
| **Notification** | 8087 | - | Event Handling | Send emails, listen to events |

---

## ⚙️ Technology Stack

**Core Framework:**
- Spring Boot 3.5.14 (Spring Cloud 2024.0.0)
- Java 21
- Maven 3.8.x

**Data & Persistence:**
- PostgreSQL 14+
- Spring Data JPA
- HikariCP
- Flyway (migrations)

**Microservices:**
- Spring Cloud Eureka (discovery)
- Spring Cloud Gateway (API routing)
- OpenFeign (inter-service calls)
- Resilience4j (circuit breaker, retry, timeout)

**Caching & Events:**
- Caffeine (in-memory cache)
- Spring ApplicationEventPublisher (event bus)

**Security:**
- Spring Security
- JWT (java-jwt library)
- BCrypt

**Mapping & Validation:**
- MapStruct (DTO mapping)
- Hibernate Validator (input validation)

**Testing:**
- JUnit 5
- Mockito
- Testcontainers (PostgreSQL)

---

## 🚀 Getting Started

### 1️⃣ Prerequisites
```bash
# Verify installations
java -version          # Java 21+
mvn --version         # Maven 3.8+
psql --version        # PostgreSQL 14+

# Verify PostgreSQL running
psql -U postgres -c "SELECT 1"
```

### 2️⃣ Orient Yourself
- Read: `docs/QUICKSTART.md` (5 min)
- Skim: `docs/constitution.md` (principles)

### 3️⃣ Prepare Database
Follow setup steps in `implement/implementation-guide.md` Part 1

### 4️⃣ Create Project Structure
Follow Maven setup in `implement/implementation-guide.md` Part 1 Step 4-5

### 5️⃣ Start Services
Follow startup order in `implement/implementation-guide.md` Part 4

### 6️⃣ Verify Health
```bash
curl http://localhost:8761/eureka/status
# Shows all registered services
```

---

## 📖 How to Use This Spec-Kit

### For Reading
1. Start with `docs/QUICKSTART.md` for orientation
2. Read phase documents in order: Constitution → Specify → Plan → Tasks → Implement
3. Bookmark `implement/implementation-guide.md` for daily reference

### For Development
1. Find your task in `tasks/tasks.md`
2. Reference patterns in `implement/implementation-guide.md`
3. Verify against acceptance criteria

### For Design Reviews
1. Share `plan/architecture-plan.md` for high-level overview
2. Deep dive into specific sections meeting context
3. Reference trade-off decisions for justification

### For Onboarding
1. Have new team members read: QUICKSTART → Constitution → Their first task
2. Pair program using implementation-guide.md as reference
3. Have them complete a task from tasks.md

---

## ⚡ Running All Services

### Terminal 1: PostgreSQL (pre-started)
```bash
# Already running from installation
psql -U postgres
```

### Terminal 2: Eureka Server
```bash
cd eureka-server
mvn spring-boot:run
```

### Terminal 3: API Gateway
```bash
cd api-gateway
mvn spring-boot:run
```

### Terminal 4-6: Auth, Product, Inventory (in parallel)
```bash
cd auth-service && mvn spring-boot:run
# (in another terminal)
cd product-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
```

### Terminal 7-9: Cart, Order, Payment (in parallel)
```bash
cd cart-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
```

### Terminal 10: Notification Service
```bash
cd notification-service && mvn spring-boot:run
```

**Verify all running:**
```bash
curl http://localhost:8761/eureka/apps | grep -o '"instanceId":"[^"]*"'
# Should show 9 services
```

---

## 🧪 Testing Approach

**Unit Tests** (service layer, Mockito)
```bash
mvn clean test
```

**Integration Tests** (real PostgreSQL, Testcontainers)
```bash
mvn clean verify
```

**Concurrency Test** (proves no overselling)
- Located in `inventory-service/InventoryConcurrencyTest.java`
- 100 concurrent threads, only 50 succeed (inventory limit)

---

## 🐛 Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Service won't start | PostgreSQL not running | Verify PostgreSQL: `psql -U postgres` |
| Services not registering | Eureka not running | Start Eureka first on `:8761` |
| 401 Unauthorized | JWT expired | Call refresh token endpoint |
| OptimisticLockException | Concurrent modification | Expected, service retries automatically |
| Flyway migration failed | Schema already exists | Drop DB and recreate (dev only) |

See detailed troubleshooting in `implement/implementation-guide.md` Part 5.

---

## 📝 Document Index

| Document | Location | Purpose |
|----------|----------|---------|
| Quick Start | `docs/QUICKSTART.md` | 5-minute orientation |
| Constitution | `docs/constitution.md` | Governing principles |
| Product Spec | `specify/product-spec.md` | Feature definitions |
| Architecture Plan | `plan/architecture-plan.md` | System design |
| Development Tasks | `tasks/tasks.md` | Implementation tasks |
| Implementation Guide | `implement/implementation-guide.md` | How to code |
| This README | `spec-kit/README.md` | Navigation hub |

---

## 🎓 Learning Paths

### Path 1: 90-Minute Deep Dive
1. QUICKSTART.md (5 min)
2. Constitution.md (30 min)
3. Product Spec (20 min)
4. Architecture Plan Overview (20 min)
5. Implementation Guide Parts 1-2 (15 min)

### Path 2: Developer Onboarding (2 hours)
1. QUICKSTART.md
2. Constitution.md (parts on code quality)
3. Implementation Guide (Parts 1-3, setup + patterns)
4. First task from tasks.md

### Path 3: Architecture Review (1 hour)
1. QUICKSTART.md (context)
2. Architecture Plan (complete)
3. Trade-off decisions section

### Path 4: Daily Development (15 min)
1. Check task in tasks.md
2. Find pattern in implementation-guide.md
3. Start coding

---

## ✅ Quality Checklist

This Spec-Kit includes:

- ✅ Clear governance principles
- ✅ Complete feature specifications  
- ✅ Detailed architecture with trade-offs
- ✅ 50+ development tasks with criteria
- ✅ Practical implementation patterns
- ✅ Testing strategy & examples
- ✅ Local development guide
- ✅ Troubleshooting section
- ✅ Code review checklist
- ✅ Common gotchas documented

**Ready for:** Architecture review, sprint planning, implementation kickoff

---

## 📞 Key Decisions Documented

Every major decision in this Spec-Kit includes:
- ✅ What was chosen
- ✅ Why (rationale)
- ✅ Trade-offs vs. alternatives
- ✅ When to apply it

**Example:** See "Optimistic vs. Pessimistic Locking" decision in `plan/architecture-plan.md`

---

## 🎯 Success Criteria

This project is successful when:

1. **Architecture Approved:** All stakeholders understand and approve the design ✅
2. **Tasks Assigned:** Development team has clear, actionable tasks ✅
3. **Code Written:** Services implemented per specification ⚠️ (33% complete)
4. **Tests Passing:** >80% unit test coverage, concurrency tests pass ❌ (No tests yet)
5. **Documented:** ARCHITECTURE.md records all trade-offs ⚠️ (Partial)
6. **Deployable:** All services runs locally without errors ✅ (4/9 services complete)
7. **Reviewable:** Code reviews checking against constitution standards ✅

### Current vs Target

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Services Complete | 9/9 | 4/9 | 44% |
| Tasks Complete | 51/51 | 17/51 | 33% |
| Unit Test Coverage | >80% | 0% | ❌ |
| Integration Tests | All passing | None | ❌ |
| API Documentation | Complete | Partial | ⚠️ |

---

## 📅 Estimated Timeline

### Original Plan

| Phase | Duration | Deliverable | Status |
|-------|----------|-------------|--------|
| Planning | Week 1 | Architecture approved, tasks assigned | ✅ Complete |
| Foundation | Week 2 | Eureka, Gateway, Auth services | ✅ Complete |
| Core Services | Weeks 2-3 | Product, Inventory, Cart services | ⚠️ Product complete, Inventory/Cart missing |
| Integration | Week 4 | Order, Payment, Notification services | ❌ Not started |
| Testing | Week 4-5 | All tests passing, coverage > 80% | ❌ Not started |
| Documentation | Week 5 | ARCHITECTURE.md, runbooks | ⚠️ Partial |
| Review | Week 6 | Architecture review, sign-off | ⏳ Pending |

### Revised Estimate (Remaining Work)

| Phase | Duration | Deliverable |
|-------|----------|-------------|
| Cart Service | 2-3 days | Full cart implementation |
| Inventory Service | 3-4 days | Stock tracking with optimistic locking |
| Payment Service | 2-3 days | Mock payment with idempotency |
| Order Service | 4-5 days | Order flow, events, integration |
| Notification Service | 1-2 days | Event listeners, mock email |
| Testing | 3-4 days | Unit + integration tests |
| Documentation | 1-2 days | API docs, architecture updates |

**Total Remaining:** 3-4 weeks with 2-3 developers

---

## 📚 References

- **Spring Boot:** https://spring.io/projects/spring-boot
- **Spring Cloud:** https://spring.io/projects/spring-cloud
- **PostgreSQL:** https://www.postgresql.org/docs/
- **JWT:** https://jwt.io/
- **Resilience4j:** https://resilience4j.readme.io/
- **Testcontainers:** https://www.testcontainers.org/

---

## 📄 Document Metadata

| Property | Value |
|----------|-------|
| **Version** | 1.1 |
| **Status** | Implementation In Progress |
| **Date** | May 2026 |
| **Maintainer** | Development Team |
| **Review Cycle** | Quarterly |
| **Implementation Progress** | 33% Complete (17/51 tasks) |

---

## 🤝 Contributing

When updating this Spec-Kit:

1. Ensure consistency across all 5 documents
2. Document any architecture changes in Constitution/Plan
3. Update tasks if requirements change
4. Keep implementation-guide patterns current
5. Version all changes

---

**Start here:** Read `docs/QUICKSTART.md` (5 minutes) →

Then select your path above based on your role.

Good luck! 🚀

