# Microservices Service Discovery Implementation Guide

## Overview

This document provides a comprehensive guide for implementing proper Eureka service discovery and enforcing centralized API Gateway routing in the ecommerce platform, with database per service pattern.

## Architecture Changes Completed

### ✅ 1. API Gateway Configuration (DONE)
**File**: `api-gateway/src/main/resources/application.yml`

- **Enabled Eureka Discovery Locator**: `spring.cloud.gateway.discovery.locator.enabled=true`
- **Added Service Routes**: All routes now use `lb://SERVICE-NAME` for load balancing through Eureka
- **Route Configuration**:
  - `/api/auth/**` → `lb://auth-service`
  - `/api/products/**` → `lb://product-service`
  - `/api/inventory/**` → `lb://inventory-service`
  - `/api/orders/**` → `lb://order-service`
  - `/api/cart/**` → `lb://cart-service`
  - `/api/payments/**` → `lb://payment-service`
  - `/api/notifications/**` → `lb://notification-service`

**Why this matters**: 
- Requests are no longer hardcoded to specific ports
- Services are dynamically discovered from Eureka
- Load balancing across multiple instances is automatic
- If a service is down, the gateway returns 503 Service Unavailable

---

### ✅ 2. Eureka Configuration for All Services (DONE)

**Files Updated**:
- `auth-service/src/main/resources/application.yml`
- `product-service/src/main/resources/application.yml`
- `inventory-service/src/main/resources/application.yml`
- `order-service/src/main/resources/application.yml`
- `payment-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`

**Key Configuration Added**:
```yaml
eureka:
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

**Why these settings**:
- `prefer-ip-address: true`: Services register with their IP, not hostname (critical for local/Docker)
- `lease-renewal`: 10s interval keeps registration fresh
- `lease-expiration`: 30s timeout ensures dead services are detected quickly

---

### ✅ 3. Service Ports (DONE)

Finalized port mapping:
```
eureka-server          : 8761  (Service Registry)
api-gateway            : 8080  ← Gateway entry point
auth-service           : 8081
product-service        : 8082
inventory-service      : 8083
order-service          : 8085
payment-service        : 8086
notification-service   : 8087
```

---

### ✅ 4. Feign Clients - Service-to-Service Communication (DONE)

**File**: `order-service/src/main/java/com/ecommerce/order_service/client/`

Created Feign client interfaces:

#### 1. InventoryClient.java
```java
@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/inventory/reserve")
    InventoryReservationResponse reserve(@RequestBody InventoryReservationRequest request);
}
```

- Uses `name = "inventory-service"` (NOT `url = "http://localhost:8083"`)
- Automatically routes through Eureka service discovery
- Internally uses `lb://inventory-service` load balancer

#### 2. PaymentClient.java
```java
@FeignClient(name = "payment-service")
public interface PaymentClient {
    @PostMapping("/api/payments/process")
    PaymentResponse processPayment(@RequestBody ProcessPaymentRequest request);
}
```

#### 3. ProductClient.java
```java
@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/api/products/{id}")
    ProductResponse getProduct(@PathVariable("id") String productId);
}
```

#### 4. CartClient.java
```java
@FeignClient(name = "cart-service")
public interface CartClient {
    @GetMapping("/api/cart")
    CartResponse getCart(@RequestHeader("Authorization") String authHeader);
}
```

**Why this approach**:
- No hardcoded URLs
- Automatic retry and circuit breaker support
- Client-side load balancing
- Service discovery integrated seamlessly

---

### ✅ 5. Database Per Service (DONE)

**Database Configuration in Each Service**:

**Auth Service** (auth_db):
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db
    username: postgres
    password: 911911
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**Same pattern applied to**:
- Product Service → product_db
- Inventory Service → inventory_db
- Order Service → order_db
- Payment Service → payment_db
- Notification Service → notification_db

**Why Database Per Service**:
- ✅ Strict microservices pattern (no shared databases)
- ✅ Data isolation between services
- ✅ Independent scalability
- ✅ Freedom to choose technology per service
- ✅ Avoids tight coupling through database

---

### ✅ 6. Security Filter (DONE)

**File**: `api-gateway/src/main/java/com/ecommerce/api_gateway/filter/SecurityFilter.java`

Initial security filter to track gateway requests. This ensures:
- All requests are tagged with `X-Gateway-Request: true`
- Request paths are logged for audit trails
- Foundation for more advanced security policies

---

## What This Achieves

### ✅ Centralized Entry Point
```bash
# ✅ Works - External clients use only the gateway
curl http://localhost:8080/api/products

# ✅ Also works in local development - Direct access 
curl http://localhost:8082/api/products
```

### ✅ Eureka Service Discovery
- Services register automatically with Eureka on startup
- Gateway discovers services dynamically
- No configuration changes needed to add new instances
- Dead services automatically removed

### ✅ Service-to-Service Communication via Feign
```java
// ✅ Good - Uses service discovery
OrderService calls InventoryClient → lb://inventory-service → Eureka lookup

// ❌ Bad (NO LONGER NEEDED)
OrderService calls hardcoded URL → http://localhost:8083/...
```

### ✅ Hard Eureka Dependency
```
Eureka Down:
  - All services fail to register
  - Gateway returns 503 Service Unavailable
  - New requests cannot reach any service
  - Existing instance communication may work (based on cache)
```

### ✅ Database Per Service
```
Each service has isolated data:
  - auth-service uses auth_db
  - product-service uses product_db
  - inventory-service uses inventory_db
  - order-service uses order_db
  - payment-service uses payment_db
  - notification-service uses notification_db
```

---

## Implementation Roadmap for Services

### Auth Service (auth-service)
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        // ... authentication logic
        return new TokenResponse(accessToken, refreshToken);
    }
}
```

### Product Service (product-service)
```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        // Returns product details from product_db
    }
}
```

### Inventory Service (inventory-service)
```java
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    
    @PostMapping("/reserve")
    public InventoryReservationResponse reserve(@RequestBody ReserveRequest request) {
        // Reserves stock with optimistic locking from inventory_db
    }
}
```

### Order Service (order-service) - Using Feign Clients
```java
@RestController
@RequestMapping("/api/orders")
@EnableFeignClients
public class OrderController {
    
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final CartClient cartClient;
    
    public OrderController(InventoryClient inventoryClient, 
                          PaymentClient paymentClient,
                          CartClient cartClient) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.cartClient = cartClient;
    }
    
    @PostMapping
    public OrderResponse placeOrder(@RequestBody PlaceOrderRequest request) {
        // 1. Call inventory-service via Feign → Eureka → lb://inventory-service
        InventoryReservationResponse reservation = 
            inventoryClient.reserve(new InventoryReservationRequest(...));
        
        // 2. Call payment-service via Feign → Eureka → lb://payment-service
        PaymentResponse payment = 
            paymentClient.processPayment(new ProcessPaymentRequest(...));
        
        // 3. Call cart-service via Feign → Eureka → lb://cart-service
        cartClient.clearCart(authHeader);
        
        return new OrderResponse(orderId, orderNumber);
    }
}
```

---

## Request Flow Example

### External Client Request
```
1. Client: GET http://localhost:8080/api/products/123
                    ↓
2. API Gateway (port 8080)
   - Matches route: Path=/api/products/**
   - Strips prefix: /api/products → /products/123
   - Resolves lb://product-service
                    ↓
3. Eureka Service Registry
   - Looks up "product-service"
   - Returns: [localhost:8082]
                    ↓
4. Product Service (port 8082)
   - GET /products/123
   - Queries product_db
   - Returns: { id: "123", name: "...", price: ... }
                    ↓
5. Gateway: 200 OK - Product details returned to client
```

### Service-to-Service Request (Order → Inventory)
```
1. Order Service needs inventory
   - Calls: inventoryClient.reserve(request)
                    ↓
2. Feign Client Resolution
   - Reads: @FeignClient(name = "inventory-service")
   - Queries: Eureka for "inventory-service"
                    ↓
3. Eureka Service Registry
   - Looks up "inventory-service"
   - Returns: [localhost:8083]
                    ↓
4. Inventory Service (port 8083)
   - POST /api/inventory/reserve
   - Updates stock in inventory_db with optimistic locking
   - Returns: { reservationId: "...", status: "RESERVED" }
                    ↓
5. Order Service receives response
   - Saves order to order_db
   - Proceeds with payment processing
```

---

## Validation Tests

### 1. Test Gateway Routing
```bash
# All these should work through gateway
curl http://localhost:8080/api/auth/login
curl http://localhost:8080/api/products
curl http://localhost:8080/api/inventory/reserve
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/payments/process
curl http://localhost:8080/api/notifications

# Check Eureka Dashboard
open http://localhost:8761
# Should show all services with "UP" status
```

### 2. Test Service Discovery
```bash
# Check if services registered with Eureka
curl http://localhost:8761/eureka/apps/
# Response should include: auth-service, product-service, inventory-service, etc.
```

### 3. Test Feign Clients (After Implementation)
```bash
# Place an order (tests Order → Inventory → Payment chain)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{ "items": [...], "address": {...} }'

# Should demonstrate:
# - Order Service calls Inventory via Feign
# - Inventory Service processes reservation
# - Order Service calls Payment via Feign
# - Payment Service processes payment
```

### 4. Test Eureka Failure
```bash
# Stop Eureka server
docker stop eureka-server

# Try gateway request
curl http://localhost:8080/api/products
# Expected: 503 Service Unavailable
# Reason: No service instances available in registry

# Restart Eureka
docker start eureka-server
# Services re-register within 10 seconds
```

### 5. Test Database Isolation
```bash
# Connect to each database to verify isolation
psql -U postgres -d auth_db -c "\dt"      # Lists tables in auth_db
psql -U postgres -d product_db -c "\dt"   # Lists tables in product_db
psql -U postgres -d inventory_db -c "\dt" # Lists tables in inventory_db

# Each service manages only its own database tables
```

---

## Migration Checklist

- [ ] API Gateway routes configured with `lb://SERVICE-NAME`
- [ ] All services have Eureka client configuration
- [ ] `eureka.instance.prefer-ip-address: true` set on all services
- [ ] Database configurations added to all services
- [ ] PostgreSQL databases created for each service
- [ ] Feign clients created for inter-service communication
- [ ] Order service has `@EnableFeignClients` annotation
- [ ] Order service pom.xml includes `spring-cloud-starter-openfeign`
- [ ] Security filter added to gateway
- [ ] Test all gateway routes
- [ ] Verify Eureka dashboard shows all services UP
- [ ] Test service-to-service communication via Feign
- [ ] Verify database isolation (no shared schemas)
- [ ] Test database connectivity for each service

---

## Next Steps for Development Teams

### Auth Team
1. Implement login/register endpoints
2. Generate JWT tokens with RS256
3. Return tokens with proper expiration
4. Store user data in auth_db

### Product Team
1. Implement Product entity and repository
2. Create search/filter endpoints
3. Add caching with Caffeine
4. Store product data in product_db

### Inventory Team
1. Implement Inventory entity with optimistic locking
2. Create reservation endpoints
3. Handle concurrent modifications
4. Store inventory data in inventory_db

### Order Team
1. Implement order placement flow
2. Call Inventory Client to reserve stock
3. Call Payment Client to process payment
4. Handle compensation on failure
5. Call Cart Client to clear cart on success
6. Store order data in order_db

### Payment Team
1. Create payment processing with mock provider
2. Implement idempotency using headers
3. Support retry logic with exponential backoff
4. Store payment data in payment_db

### Notification Team
1. Listen to domain events from Order Service
2. Format and send notifications
3. Log notification delivery
4. Store notification data in notification_db

---

## Common Pitfalls to Avoid

### ❌ WRONG: Hardcoded URLs in Feign Clients
```java
@FeignClient(url = "http://localhost:8083")  // ❌ DON'T DO THIS
interface InventoryClient { }
```

### ✅ CORRECT: Use Service Name
```java
@FeignClient(name = "inventory-service")  // ✅ DO THIS
interface InventoryClient { }
```

### ❌ WRONG: Shared Database Between Services
```yaml
# ❌ DON'T DO THIS
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce-server
```

### ✅ CORRECT: Database Per Service
```yaml
# ✅ DO THIS
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db
```

### ❌ WRONG: Missing Eureka registration
```yaml
eureka:
  client:
    register-with-eureka: false  # ❌ DON'T SET TO FALSE
```

### ✅ CORRECT: Always register services
```yaml
eureka:
  client:
    register-with-eureka: true  # ✅ OR JUST OMIT (defaults to true)
```

---

## Troubleshooting

### Issue: "No instances available" error
**Cause**: Service not registered with Eureka
**Solution**: 
- Check service is running
- Verify `eureka.client.service-url.defaultZone` is correct
- Check logs for Eureka registration errors
- Visit http://localhost:8761 to see registered services

### Issue: Gateway returns 504 Gateway Timeout
**Cause**: Backend service not responding
**Solution**:
- Check if service is running
- Check service logs for errors
- Verify routes in gateway configuration
- Ensure Eureka has latest service registry

### Issue: Database connection error
**Cause**: Wrong database name or credentials
**Solution**:
- Verify PostgreSQL is running
- Verify database exists (CREATE DATABASE)
- Check connection URL is correct
- Verify username and password
- Check firewall/network access to port 5432

### Issue: "Request marked as consumed" from Feign
**Cause**: Request body already read
**Solution**:
- Use `@RequestBody` on Feign method parameters
- Ensure request body is serializable
- Check for request/response interceptors consuming body

### Issue: Services can't communicate internally
**Cause**: Wrong database or configuration
**Solution**:
- Verify each service uses its own database
- Check Eureka registration
- Verify service names match Feign client names
- Check for network issues

---

## References

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Spring Cloud Eureka Documentation](https://spring.io/projects/spring-cloud-netflix)
- [Spring Cloud OpenFeign Documentation](https://spring.io/projects/spring-cloud-openfeign)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

---

**Last Updated**: May 1, 2026
**Status**: Implementation Guide Complete

