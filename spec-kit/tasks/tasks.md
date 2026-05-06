# Development Tasks: E-Commerce Platform

## Overview

This document outlines the concrete development tasks required to build the e-commerce platform according to the specification and architecture. Tasks are organized by service and phase, with clear acceptance criteria.

**Total Services:** 9  
**Estimated Timeline:** 4-6 weeks (with team)  
**Last Updated:** April 2026

---

## Scope

This task list covers:
- Backend service implementation
- Database migrations
- Integration with other services
- Unit and integration testing
- Configuration and deployment setup

Out of scope:
- Frontend development
- DevOps deployment pipeline
- Production monitoring setup
- Customer support dashboard

---

## Foundation Phase

### Task F1: Eureka Server Setup

**Service:** eureka-server `:8761`  
**Owner:** Infrastructure Team  
**Duration:** 2 hours  
**Dependencies:** None

#### Acceptance Criteria

- [x] Eureka Server runs on port 8761
- [x] Dashboard accessible at http://localhost:8761
- [x] No authentication required (dev mode)
- [x] Running locally, not containerized
- [x] Spring Cloud Netflix dependency configured

#### Implementation Steps

1. Create new Spring Boot project: `eureka-server`
2. Add dependency: `spring-cloud-starter-netflix-eureka-server`
3. Enable Eureka with `@EnableEurekaServer` on main class
4. Configure `application.yaml`:
   - Port: 8761
   - Disable self-registration
5. Verify startup logs show Eureka dashboard ready

---

### Task F2: API Gateway Setup

**Service:** api-gateway `:8080`  
**Owner:** Infrastructure Team  
**Duration:** 4 hours  
**Dependencies:** Eureka Server running

#### Acceptance Criteria

- [x] Gateway runs on port 8080
- [x] Routes requests to backend services via Eureka
- [x] JWT validation filter validates access tokens
- [x] Invalid JWT returns 401 Unauthorized
- [x] Correlation ID injected on all requests
- [x] CORS headers added for browser requests
- [x] Rate limiting implemented (100 req/min per user)
- [x] Global exception handler returns RFC 7807 errors

#### Implementation Steps

1. Create new Spring Boot project: `api-gateway`
2. Add dependency: `spring-cloud-starter-gateway`
3. Configure routes to all 6 backend services (auth, product, inventory, cart, order, payment)
4. Implement JWT validation filter (validate RS256 signature)
5. Extract user ID from JWT, add to request headers
6. Implement correlation ID filter
7. Configure CORS (allow localhost:4200 for frontend)
8. Rate limit configuration per user
9. Global exception handler for all errors

#### Testing

```
- POST http://localhost:8080/api/auth/login → forwarded to 8081
- GET http://localhost:8080/api/products → forwarded to 8082
- Test with invalid JWT → 401 response
- Test rate limit exceeded → 429 response
```

---

## Auth Service Phase

### Task A1: Auth Service Scaffolding

**Service:** auth-service `:8081`  
**Owner:** Auth Team  
**Duration:** 3 hours  
**Dependencies:** Eureka Server running

#### Acceptance Criteria

- [x] Spring Boot project created and running on :8081
- [x] Registers with Eureka
- [x] PostgreSQL connection working
- [x] Password encoder configured (BCrypt strength 12)
- [x] JWT key pair generated and stored

#### Implementation Steps

1. Create new Spring Boot project: `auth-service`
2. Add dependencies: Spring Data JPA, PostgreSQL driver, Spring Security, JWT library
3. Configure Eureka registration in `application.yaml`
4. Configure PostgreSQL connection: `auth_db`
5. Generate RSA key pair (2048-bit) for JWT RS256
   - Store public key for gateway validation
   - Store private key in `keystore.p12`
6. Create `BCryptPasswordEncoder` bean with strength 12
7. Verify service starts and registers with Eureka

---

### Task A2: User Entity & Repository

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 2 hours  
**Dependencies:** Task A1 complete

#### Acceptance Criteria

- [x] User JPA entity created
- [x] UserRepository Spring Data repository created
- [x] Table created via Flyway migration
- [x] Find by email query implemented
- [x] Find by ID query implemented
- [x] Unit tests pass for repository

#### Implementation Steps

1. Create `User` JPA entity:
   ```
   - id: UUID (@Id)
   - email: String (unique, not null)
   - passwordHash: String (BCrypt)
   - fullName: String
   - roles: Set<Role>
   - createdAt: Instant
   - updatedAt: Instant
   - version: long (@Version for optimistic lock)
   ```
2. Create `Role` enum: USER, ADMIN
3. Create `UserRepository` interface extending `JpaRepository`
4. Create Flyway migration `V1__init_auth_db.sql`:
   ```sql
   CREATE TABLE users (
     id UUID PRIMARY KEY,
     email VARCHAR(255) UNIQUE NOT NULL,
     password_hash VARCHAR(255) NOT NULL,
     full_name VARCHAR(255) NOT NULL,
     created_at TIMESTAMP NOT NULL,
     updated_at TIMESTAMP NOT NULL,
     version BIGINT DEFAULT 0,
     roles VARCHAR(50) NOT NULL DEFAULT 'USER'
   );
   ```
5. Unit test repository CRUD operations

---

### Task A3: Registration Endpoint

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 3 hours  
**Dependencies:** Task A2 complete

#### Acceptance Criteria

- [x] POST /api/auth/register endpoint created
- [x] Email validation (format + uniqueness)
- [x] Password validation (8+ chars, 1 upper, 1 lower, 1 digit)
- [x] Duplicate email returns 409 Conflict
- [x] Invalid email returns 400 Bad Request
- [x] Weak password returns 400 Bad Request
- [x] Response contains user UUID and timestamp
- [x] Password hashed with BCrypt before storage

#### Implementation Steps

1. Create `RegisterRequest` record:
   ```
   - email: String
   - password: String
   - fullName: String
   ```
2. Create `UserResponse` record:
   ```
   - id: UUID
   - email: String
   - fullName: String
   - roles: Set<String>
   - createdAt: Instant
   ```
3. Create `IUserService` interface
4. Create `UserService` implementation
   - Method: `register(RegisterRequest)`
   - Validate email format
   - Check email uniqueness
   - Validate password strength
   - Hash password with BCrypt
   - Save user with USER role
   - Return response
5. Create `UserController`
   - Endpoint: `POST /api/auth/register`
   - Validation: `@Valid` on request
   - Exception handling: return proper error codes

#### Testing

```
POST /api/auth/register
{
  "email": "user@example.com",
  "password": "Password123",
  "fullName": "John Doe"
}

Response (201):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "fullName": "John Doe",
  "roles": ["USER"],
  "createdAt": "2026-04-30T14:23:45Z"
}
```

---

### Task A4: JWT Token Management

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 4 hours  
**Dependencies:** Task A3 complete

#### Acceptance Criteria

- [x] Access token generated with RS256 signature
- [x] Access token expires in 15 minutes
- [x] Refresh token generated
- [x] Refresh token expires in 7 days
- [x] Tokens contain user ID and roles
- [x] Token validation verifies signature and expiration
- [x] Invalid signature rejected
- [x] Expired token rejected

#### Implementation Steps

1. Create `TokenProvider` utility class
   - Method: `generateAccessToken(User)` → String
   - Method: `generateRefreshToken(User)` → String
   - Method: `validateToken(String)` → boolean
   - Method: `getUserIdFromToken(String)` → UUID
   - Use java-jwt library with RS256
2. Create `TokenResponse` record:
   ```
   - accessToken: String
   - refreshToken: String
   - expiresIn: long (15 * 60 seconds)
   - tokenType: String ("Bearer")
   ```
3. Integration with `UserService.login(email, password)`
4. Unit tests for token generation and validation

#### Token Payload

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "roles": ["USER"],
  "iat": 1714511025,
  "exp": 1714511925
}
```

---

### Task A5: Login Endpoint

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 2 hours  
**Dependencies:** Task A4 complete

#### Acceptance Criteria

- [x] POST /api/auth/login endpoint created
- [x] Valid credentials return access + refresh tokens
- [x] Invalid credentials return 401 Unauthorized
- [x] Email not found returns 401 (not 404, don't leak user existence)
- [x] Password mismatch returns 401

#### Implementation Steps

1. Create `LoginRequest` record:
   ```
   - email: String
   - password: String
   ```
2. Create `UserService.login(email, password)` method
   - Find user by email
   - If not found: throw AuthenticationException
   - If found: compare password with BCrypt
   - If match: generate tokens and return
   - If mismatch: throw AuthenticationException
3. Create endpoint in `UserController`:
   ```
   POST /api/auth/login
   Returns: TokenResponse
   ```
4. Exception handler returns 401 for AuthenticationException

---

### Task A6: Refresh Token Endpoint

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 1 hour  
**Dependencies:** Task A5 complete

#### Acceptance Criteria

- [x] POST /api/auth/refresh endpoint created
- [x] Valid refresh token returns new access token
- [x] Expired refresh token returns 401 Unauthorized
- [x] Invalid signature returns 401 Unauthorized

#### Implementation Steps

1. Create `RefreshRequest` record:
   ```
   - refreshToken: String
   ```
2. Create `UserService.refreshToken(refreshToken)` method
   - Validate token signature and expiration
   - Extract user ID
   - Generate new access token
   - Return TokenResponse
3. Endpoint: `POST /api/auth/refresh`
4. Error handling for invalid tokens

---

### Task A7: Get Current User Endpoint

**Service:** auth-service  
**Owner:** Auth Team  
**Duration:** 1 hour  
**Dependencies:** Task A3 complete

#### Acceptance Criteria

- [x] GET /api/auth/me endpoint created
- [x] Returns current authenticated user profile
- [x] Requires valid JWT in Authorization header
- [x] Returns 401 if no token
- [x] Returns 401 if token invalid

#### Implementation Steps

1. Extract user ID from JWT token (via filter)
2. Create endpoint: `GET /api/auth/me`
3. Retrieve user from database
4. Return full profile (id, email, name, roles, addresses)

---

## Product Service Phase

### Task P1: Product Service Scaffolding

**Service:** product-service `:8082`  
**Owner:** Product Team  
**Duration:** 2 hours  
**Dependencies:** Eureka Server running

#### Acceptance Criteria

- [x] Spring Boot project created and running on :8082
- [x] Registers with Eureka
- [x] PostgreSQL connection to `product_db` working
- [x] Caffeine cache configured

#### Implementation Steps

1. Create Spring Boot project: `product-service`
2. Add dependencies: Spring Data JPA, PostgreSQL driver, Caffeine, MapStruct
3. Register with Eureka
4. Configure PostgreSQL connection string
5. Create `@Configuration` class for Caffeine cache bean with 15-min TTL

---

### Task P2: Product Entity & Repository

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 2 hours  
**Dependencies:** Task P1 complete

#### Acceptance Criteria

- [x] Product and Category JPA entities created
- [x] ProductRepository with search queries
- [x] Flyway migration created and applied
- [x] Queries support search by name/description
- [x] Queries support filter by category

#### Implementation Steps

1. Create `Product` entity:
   ```
   - id: UUID
   - name: String (searchable index)
   - description: String (searchable)
   - price: BigDecimal (> 0)
   - category: Category (FK)
   - imageUrl: String (optional)
   - createdAt: Instant
   - updatedAt: Instant
   ```
2. Create `Category` entity
3. Create `ProductRepository`:
   - `findByNameOrDescriptionContainsIgnoreCase(name, description)`
   - `findByCategory(category)`
   - `findByPriceBetween(min, max)`
4. Flyway migration

---

### Task P3: List Products with Search/Filter

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 3 hours  
**Dependencies:** Task P2 complete

#### Acceptance Criteria

- [x] GET /api/products endpoint with query parameters
- [x] Search by keyword (name, description)
- [x] Filter by category
- [x] Filter by price range
- [x] Pagination working (page, size)
- [x] Response includes total count
- [x] Results cached in Caffeine for 15 minutes

#### Implementation Steps

1. Create `ProductSearchRequest` record:
   ```
   - keyword: Optional<String>
   - category: Optional<String>
   - minPrice: Optional<BigDecimal>
   - maxPrice: Optional<BigDecimal>
   - page: int (default 0)
   - size: int (default 25)
   ```
2. Create `IProductService.searchProducts(request)` → Page<ProductDTO>
3. Implement caching with `@Cacheable("product-cache")`
4. Endpoint: `GET /api/products?keyword=...&category=...&page=0&size=25`

---

### Task P4: Get Single Product

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 1 hour  
**Dependencies:** Task P3 complete

#### Acceptance Criteria

- [x] GET /api/products/{id} endpoint
- [x] Returns product details with image
- [x] Returns 404 if not found
- [x] Response cached

#### Implementation Steps

1. Endpoint: `GET /api/products/{id}`
2. `IProductService.getProduct(id)` → ProductDTO
3. Cache result
4. Exception handler for ProductNotFoundException

---

### Task P5: Create Product (Admin)

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 2 hours  
**Dependencies:** Task P4 complete

#### Acceptance Criteria

- [x] POST /api/products endpoint (admin only)
- [x] Requires @PreAuthorize("hasRole('ADMIN')")
- [x] Validates all required fields
- [x] Price > 0
- [x] Returns created product
- [x] Returns 403 if not admin

#### Implementation Steps

1. Create `CreateProductRequest` record
2. `IProductService.createProduct(request)` → ProductDTO
3. Validation: price > 0
4. Save to DB
5. Invalidate product cache
6. Controller endpoint with `@PreAuthorize("hasRole('ADMIN')")`

---

### Task P6: Update Product (Admin)

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 2 hours  
**Dependencies:** Task P5 complete

#### Acceptance Criteria

- [x] PUT /api/products/{id} endpoint (admin only)
- [x] All fields optional
- [x] Non-existent product returns 404
- [x] Returns 403 if not admin

#### Implementation Steps

1. `IProductService.updateProduct(id, request)` → ProductDTO
2. Partial update support (only update provided fields)
3. Invalidate cache
4. Return updated entity

---

### Task P7: Delete Product (Admin)

**Service:** product-service  
**Owner:** Product Team  
**Duration:** 1 hour  
**Dependencies:** Task P6 complete

#### Acceptance Criteria

- [x] DELETE /api/products/{id} endpoint (admin only)
- [x] Soft delete (mark as deleted)
- [x] Product no longer appears in search
- [x] Order history preserved

#### Implementation Steps

1. Add `deleted` boolean field to Product
2. Soft delete implementation
3. Exclude deleted products from all queries
4. Invalidate cache

---

## Inventory Service Phase

### Task I1: Inventory Service Scaffolding

**Service:** inventory-service `:8083`  
**Owner:** Inventory Team  
**Duration:** 2 hours  
**Dependencies:** Eureka Server, Product Service deployed

#### Acceptance Criteria

- [ ] Service running on :8083
- [ ] Registers with Eureka
- [ ] PostgreSQL connection to `inventory_db`
- [ ] Resilience4j configured for circuit breaker

#### Implementation Steps

1. Create Spring Boot project: `inventory-service`
2. Add Resilience4j + Spring Boot Actuator
3. Configure circuit breaker for downstream calls
4. Register with Eureka

---

### Task I2: Inventory Entity with Optimistic Locking

**Service:** inventory-service  
**Owner:** Inventory Team  
**Duration:** 2 hours  
**Dependencies:** Task I1 complete

#### Acceptance Criteria

- [ ] Inventory entity with @Version field
- [ ] Reservation tracking
- [ ] Migration applied
- [ ] Concurrent modification simulation test passes

#### Implementation Steps

1. Create `Inventory` entity:
   ```
   - productId: UUID (@Id)
   - totalStock: int
   - reservedStock: int
   - version: long (@Version)
   - updatedAt: Instant
   ```
2. Create `InventoryRepository`
3. Flyway migration with version column

---

### Task I3: Reserve Stock Endpoint

**Service:** inventory-service  
**Owner:** Inventory Team  
**Duration:** 3 hours  
**Dependencies:** Task I2 complete

#### Acceptance Criteria

- [ ] POST /api/inventory/reserve endpoint
- [ ] Atomically reserves stock with optimistic locking
- [ ] Retries up to 3 times on OptimisticLockException
- [ ] Returns 409 if insufficient stock after retries
- [ ] Returns reservation ID

#### Implementation Steps

1. Create `ReserveRequest` record: productId, quantity, orderId
2. Create `IInventoryService.reserveStock(request)` → ReservationResponse
3. Implementation with @Transactional and optimistic locking
4. Retry logic with backoff (10ms, 20ms, 40ms)
5. Exception handling for business errors

---

### Task I4: Confirm Reservation Endpoint

**Service:** inventory-service  
**Owner:** Inventory Team  
**Duration:** 2 hours  
**Dependencies:** Task I3 complete

#### Acceptance Criteria

- [ ] POST /api/inventory/confirm endpoint
- [ ] Decrements total stock, clears reservation
- [ ] Idempotent (confirming twice = same result)
- [ ] Reservation not found returns 404

#### Implementation Steps

1. Create `ConfirmRequest` record: reservationId
2. `IInventoryService.confirmReservation(request)`
3. Mark reservation as confirmed
4. Decrement total stock
5. Test idempotency

---

### Task I5: Release Reservation Endpoint

**Service:** inventory-service  
**Owner:** Inventory Team  
**Duration:** 2 hours  
**Dependencies:** Task I3 complete

#### Acceptance Criteria

- [ ] POST /api/inventory/release endpoint
- [ ] Returns reserved stock to available
- [ ] Idempotent
- [ ] Reservation not found returns 404

#### Implementation Steps

1. Create `ReleaseRequest` record: reservationId
2. `IInventoryService.releaseReservation(request)`
3. Mark as released
4. Test idempotency

---

### Task I6: Concurrent Reservation Test

**Service:** inventory-service  
**Owner:** Inventory Team  
**Duration:** 4 hours  
**Dependencies:** Task I5 complete

#### Acceptance Criteria

- [ ] 100 concurrent threads attempt to reserve same product
- [ ] Only 50 succeed (inventory has 50 units)
- [ ] No partial reservations
- [ ] All retries properly handled
- [ ] Test uses real PostgreSQL (Testcontainers)

#### Implementation Steps

1. Create concurrency test using ExecutorService
2. Create 100 concurrent reserve requests
3. Assertions: 50 success, 50 failure
4. Use Testcontainers for PostgreSQL
5. Verify no race conditions

---

## Cart Service Phase

### Task C1: Cart Service Scaffolding

**Service:** cart-service `:8084`  
**Owner:** Cart Team  
**Duration:** 2 hours  
**Dependencies:** Eureka, Product Service available

#### Acceptance Criteria

- [ ] Service running on :8084
- [ ] Registers with Eureka
- [ ] PostgreSQL connection to `cart_db`

---

### Task C2: Cart Entity with Optimistic Locking

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 2 hours  
**Dependencies:** Task C1 complete

#### Acceptance Criteria

- [ ] Cart and CartItem entities created
- [ ] Optimistic locking on Cart header
- [ ] Migration applied

#### Implementation Steps

1. Create `Cart` entity:
   ```
   - id: UUID (@Id)
   - userId: UUID
   - items: Set<CartItem>
   - version: long (@Version)
   - updatedAt: Instant
   ```
2. Create `CartItem` entity:
   ```
   - id: UUID
   - cart: Cart
   - productId: UUID
   - quantity: int
   - priceSnapshot: BigDecimal
   ```
3. Create repositories
4. Migration with version field

---

### Task C3: View Cart Endpoint

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 2 hours  
**Dependencies:** Task C2 complete

#### Acceptance Criteria

- [ ] GET /api/cart endpoint (authenticated)
- [ ] Returns current user's cart
- [ ] Includes items with product details
- [ ] Shows subtotal per item and cart total

#### Implementation Steps

1. Extract user ID from JWT (Authorization header)
2. Retrieve cart from DB
3. Calculate totals
4. Return CartResponse

---

### Task C4: Add to Cart Endpoint

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 2 hours  
**Dependencies:** Task C3 complete

#### Acceptance Criteria

- [ ] POST /api/cart/items endpoint
- [ ] Creates cart if doesn't exist
- [ ] Increments quantity if product already in cart
- [ ] Returns updated cart

#### Implementation Steps

1. Create `AddToCartRequest` record: productId, quantity
2. Implement add logic with quantity increment
3. Handle concurrent modifications (optimistic lock)
4. Return updated cart

---

### Task C5: Update Cart Item Quantity

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 2 hours  
**Dependencies:** Task C4 complete

#### Acceptance Criteria

- [ ] PUT /api/cart/items/{productId} endpoint
- [ ] Sets quantity to exact value
- [ ] Quantity 0 removes item
- [ ] Handles concurrent modifications

#### Implementation Steps

1. Create `UpdateQuantityRequest` record: quantity
2. If quantity > 0: update item
3. If quantity = 0: remove item
4. Optimistic lock handling with 409 response

---

### Task C6: Remove from Cart Endpoint

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 1 hour  
**Dependencies:** Task C5 complete

#### Acceptance Criteria

- [ ] DELETE /api/cart/items/{productId} endpoint
- [ ] Removes item from cart
- [ ] Returns 404 if item not in cart

---

### Task C7: Clear Cart Endpoint

**Service:** cart-service  
**Owner:** Cart Team  
**Duration:** 1 hour  
**Dependencies:** Task C6 complete

#### Acceptance Criteria

- [ ] DELETE /api/cart endpoint
- [ ] Removes all items
- [ ] Returns empty cart response

---

## Order Service Phase

### Task O1: Order Service Scaffolding

**Service:** order-service `:8085`  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Eureka, Inventory Service, Payment Service available

#### Acceptance Criteria

- [ ] Service running on :8085
- [ ] Registers with Eureka
- [ ] PostgreSQL connection to `order_db`
- [ ] Feign clients configured for inventory + payment services

---

### Task O2: Order Entity & Status Enum

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O1 complete

#### Acceptance Criteria

- [ ] Order and OrderItem entities created
- [ ] OrderStatus enum with all states
- [ ] Migration applied
- [ ] Relationships configured

#### Implementation Steps

1. Create `OrderStatus` enum: PENDING, AWAITING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED
2. Create `Order` entity:
   ```
   - id: UUID (@Id)
   - orderNumber: String (ORD-YYYY-XXXXXX format)
   - userId: UUID
   - status: OrderStatus
   - items: Set<OrderItem>
   - deliveryAddress: Address (embedded)
   - totalPrice: BigDecimal
   - createdAt: Instant
   ```
3. Create `OrderItem` entity
4. Idempotency key table: `OrderIdempotencyKey`
5. Outbox table for events

---

### Task O3: Feign Client for Inventory Service

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O1 complete

#### Acceptance Criteria

- [ ] InventoryClient Feign interface created
- [ ] Circuit breaker configured
- [ ] Retry logic automatic
- [ ] Fallback method provided
- [ ] Integration tests pass

#### Implementation Steps

1. Create `IInventoryClient` Feign interface
2. Configure endpoint: POST /api/inventory/reserve
3. Add Resilience4j circuit breaker
4. Fallback: throw OutOfStockException
5. Test with real service

---

### Task O4: Feign Client for Payment Service

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O1 complete

#### Acceptance Criteria

- [ ] PaymentClient Feign interface created
- [ ] Circuit breaker configured
- [ ] Automatic retry logic
- [ ] Fallback method provided

#### Implementation Steps

1. Create `IPaymentClient` Feign interface
2. Configure endpoint: POST /api/payments/process
3. Add circuit breaker + retry
4. Fallback: throw PaymentServiceUnavailableException

---

### Task O5: Place Order Endpoint (Happy Path)

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 4 hours  
**Dependencies:** Tasks O2-O4 complete

#### Acceptance Criteria

- [ ] POST /api/orders endpoint
- [ ] Validates cart not empty
- [ ] Calls inventory service to reserve stock
- [ ] Calls payment service to process payment
- [ ] Creates order on success
- [ ] Returns created order with ID
- [ ] Publishes OrderPaidEvent
- [ ] Cart cleared after success

#### Implementation Steps

1. Create `PlaceOrderRequest` record
2. Create `OrderResponse` record
3. Implement order placement flow:
   - Validate cart
   - Call inventory.reserve()
   - On success: call payment.process()
   - On payment success: create order + publish event
   - On failure: call inventory.release() (compensation)
4. Return OrderResponse
5. Clear cart via Cart Service

---

### Task O6: Idempotent Order Placement

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 3 hours  
**Dependencies:** Task O5 complete

#### Acceptance Criteria

- [ ] Duplicate requests with same Idempotency-Key return same order
- [ ] Duplicate payment calls prevented
- [ ] No double-charging possible
- [ ] Test with concurrent requests

#### Implementation Steps

1. Extract Idempotency-Key header
2. Check if exists in OrderIdempotencyKey table
3. If yes: return existing order
4. If no: proceed with order creation, store key + order ID
5. Test: send same order twice, verify same response
6. Test: concurrent requests with same key

---

### Task O7: View Order Endpoint

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O5 complete

#### Acceptance Criteria

- [ ] GET /api/orders/{id} endpoint
- [ ] Users see only their own orders
- [ ] Admins see all orders
- [ ] Returns full order details including items and address
- [ ] Returns 404 if not found

#### Implementation Steps

1. Extract user ID and roles from JWT
2. Check authorization (owner or admin)
3. Return OrderResponse with full details

---

### Task O8: Order History with Filtering

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O7 complete

#### Acceptance Criteria

- [ ] GET /api/orders endpoint (paginated)
- [ ] Filter by status
- [ ] Newest orders first
- [ ] Users see only their orders
- [ ] Pagination: 10 items default

#### Implementation Steps

1. Create `OrderFilter` record: status (optional)
2. Implement queries with filtering
3. Paginate results
4. Authorization checks

---

### Task O9: Cancel Order Endpoint

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 3 hours  
**Dependencies:** Task O8 complete

#### Acceptance Criteria

- [ ] POST /api/orders/{id}/cancel endpoint
- [ ] Only PENDING and AWAITING_PAYMENT can cancel
- [ ] Releases inventory reservation
- [ ] Initiates refund
- [ ] Updates status to CANCELLED
- [ ] Returns order response

#### Implementation Steps

1. Check current status
2. If not cancellable: return 409 Conflict
3. Release inventory via inventory-service
4. Update order status to CANCELLED
5. Publish OrderCancelledEvent
6. Return updated order

---

### Task O10: Domain Events & Event Publisher

**Service:** order-service  
**Owner:** Order Team  
**Duration:** 2 hours  
**Dependencies:** Task O5 complete

#### Acceptance Criteria

- [ ] OrderPaidEvent defined
- [ ] OrderCancelledEvent defined
- [ ] Events published via ApplicationEventPublisher
- [ ] Notification Service listens to events
- [ ] Integration tests verify event publishing

#### Implementation Steps

1. Create domain event classes extending `DomainEvent`
2. Add event publisher to order service
3. Publish on order creation, cancellation
4. Create integration test with Testcontainers

---

## Payment Service Phase

### Task PM1: Payment Service Scaffolding

**Service:** payment-service `:8086`  
**Owner:** Payment Team  
**Duration:** 2 hours  
**Dependencies:** Eureka Server running

#### Acceptance Criteria

- [ ] Service running on :8086
- [ ] PostgreSQL connection to `payment_db`
- [ ] Mock payment configuration loaded

---

### Task PM2: Payment Entity & Idempotency

**Service:** payment-service  
**Owner:** Payment Team  
**Duration:** 2 hours  
**Dependencies:** Task PM1 complete

#### Acceptance Criteria

- [ ] Payment entity created
- [ ] IdempotencyKey entity created
- [ ] Migration applied
- [ ] Queries for idempotency working

#### Implementation Steps

1. Create `Payment` entity:
   ```
   - id: UUID
   - orderId: UUID
   - amount: BigDecimal
   - status: PaymentStatus
   - transactionId: String
   - attempts: int
   - lastAttemptAt: Instant
   ```
2. Create `IdempotencyKey` entity with result
3. Migration

---

### Task PM3: Mock Payment Provider

**Service:** payment-service  
**Owner:** Payment Team  
**Duration:** 2 hours  
**Dependencies:** Task PM2 complete

#### Acceptance Criteria

- [ ] MockPaymentProvider class created
- [ ] Success rate configurable (95% default)
- [ ] Random success/failure based on rate
- [ ] Returns mock transaction ID
- [ ] Simulation supports failure reasons

#### Implementation Steps

1. Create `MockPaymentProvider` bean
2. Configuration property: `payment.mock.success-rate`
3. Simulate payment: random decision
4. Return TransactionId on success
5. Return error reason on failure

---

### Task PM4: Process Payment Endpoint

**Service:** payment-service  
**Owner:** Payment Team  
**Duration:** 3 hours  
**Dependencies:** Task PM3 complete

#### Acceptance Criteria

- [ ] POST /api/payments/process endpoint
- [ ] Accepts idempotency key
- [ ] Retries up to 3 times with backoff (1s, 2s, 4s)
- [ ] Returns success or failure after final attempt
- [ ] Returns 402 if payment declined
- [ ] Duplicate calls with same key return same result

#### Implementation Steps

1. Create `ProcessPaymentRequest` record: amount, orderId, idempotencyKey
2. Check idempotency key first (return cached result if exists)
3. Implement retry logic with backoff
4. Store result with idempotency key
5. Return PaymentResponse

---

### Task PM5: Payment Integration Test

**Service:** payment-service  
**Owner:** Payment Team  
**Duration:** 2 hours  
**Dependencies:** Task PM4 complete

#### Acceptance Criteria

- [ ] Integration test with real PostgreSQL
- [ ] Test idempotency behavior
- [ ] Test retry logic with timing
- [ ] Test concurrent payments

#### Implementation Steps

Use Testcontainers for PostgreSQL, verify all scenarios

---

## Notification Service Phase

### Task N1: Notification Service Scaffolding

**Service:** notification-service `:8087`  
**Owner:** Notification Team  
**Duration:** 1 hour  
**Dependencies:** Eureka Server running

#### Acceptance Criteria

- [ ] Service running on :8087
- [ ] Stateless (no database)
- [ ] Registers with Eureka

---

### Task N2: Event Listeners

**Service:** notification-service  
**Owner:** Notification Team  
**Duration:** 3 hours  
**Dependencies:** Task N1 complete, Order Service emitting events

#### Acceptance Criteria

- [ ] @EventListener for OrderPaidEvent
- [ ] @EventListener for OrderCancelledEvent
- [ ] Mock email output to console
- [ ] All events received and processed
- [ ] Integration test verifies event reception

#### Implementation Steps

1. Create event listener methods
2. Implement mock email formatting
3. Console output shows email details
4. Integration test with ApplicationContext

---

### Task N3: Mock Email Service

**Service:** notification-service  
**Owner:** Notification Team  
**Duration:** 2 hours  
**Dependencies:** Task N2 complete

#### Acceptance Criteria

- [ ] Email service interface created
- [ ] Mock implementation logs to console
- [ ] Email content properly formatted
- [ ] Ready for SMTP integration

#### Implementation Steps

1. Create `IEmailService` interface
2. Create `MockEmailService` implementation
3. Format email with templates
4. Log structured output

---

## Testing Phase

### Task T1: Unit Tests - Auth Service

**Service:** auth-service  
**Owner:** Test Team  
**Duration:** 3 hours  
**Dependencies:** Auth service complete

#### Acceptance Criteria

- [ ] UserService tests with Mockito
- [ ] Password hashing verified
- [ ] Token generation tested
- [ ] Token validation tested
- [ ] All paths covered (>80%)

---

### Task T2: Unit Tests - All Other Services

**Service:** All  
**Owner:** Test Team  
**Duration:** 8 hours  
**Dependencies:** All services complete

#### Acceptance Criteria

- [ ] All public methods tested
- [ ] Edge cases covered
- [ ] Happy path + failure scenarios
- [ ] Overall coverage > 75%

---

### Task T3: Integration Tests - Order Placement

**Service:** order-service  
**Owner:** Test Team  
**Duration:** 4 hours  
**Dependencies:** Order service + dependencies

#### Acceptance Criteria

- [ ] End-to-end order placement tested
- [ ] Inventory reservation verified
- [ ] Payment processing verified
- [ ] Event publishing verified
- [ ] Uses real PostgreSQL (Testcontainers)

---

## Documentation Phase

### Task D1: ARCHITECTURE.md

**Service:** All  
**Owner:** Tech Lead  
**Duration:** 3 hours  
**Dependencies:** All services deployed

#### Acceptance Criteria

- [ ] Trade-off decisions documented
- [ ] Data flow diagrams included
- [ ] Concurrency strategy explained
- [ ] Failure scenarios covered

---

## Summary

**Total Tasks:** 50+  
**Estimated Duration:** 6-8 weeks with 3-4 developers  
**Critical Path:** Infrastructure → Auth → Product → Inventory → Order → Payment → Notification

---

## Next Steps

1. Create technical spike for Testcontainers setup
2. Set up CI/CD pipeline (GitHub Actions)
3. Plan code review process
4. Schedule architecture review kickoff meeting

