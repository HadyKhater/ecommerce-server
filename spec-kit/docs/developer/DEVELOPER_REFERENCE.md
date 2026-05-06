# Developer Quick Reference Card

## 🚀 Start Services

### Local Development (Without Docker)
```bash
# Terminal 1
cd eureka-server && mvn spring-boot:run

# Terminal 2
cd api-gateway && mvn spring-boot:run

# Terminal 3+
cd auth-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

---

## 🌐 API Endpoints

### All External Requests Go Through Gateway

```bash
# Gateway (External Entry Point)
http://localhost:8080

# Internal Services (Direct access BLOCKED - 403 without X-Gateway-Secret)
http://localhost:8081  # auth-service
http://localhost:8082  # product-service
http://localhost:8083  # inventory-service
http://localhost:8085  # order-service
http://localhost:8086  # payment-service
http://localhost:8087  # notification-service

# Monitoring
http://localhost:8761  # Eureka Dashboard
```

---

## 📊 Eureka Dashboard

```
http://localhost:8761

Check:
- Service Registration Status (should show UP)
- Instance count per service
- Service health
- Instance metadata
```

---

## 🔍 Sample Requests

### Through Gateway (✅ Recommended)
```bash
# Auth
curl http://localhost:8080/api/auth/
curl http://localhost:8080/api/auth/login -X POST

# Products
curl http://localhost:8080/api/products

# Orders
curl http://localhost:8080/api/orders

# Inventory
curl http://localhost:8080/api/inventory

# Payments
curl http://localhost:8080/api/payments

# Notifications
curl http://localhost:8080/api/notifications

# Cart
curl http://localhost:8080/api/cart
```

### Direct (❌ Blocked — 403 Forbidden)
```bash
# Direct access to backend services is blocked
# Returns 403 without valid X-Gateway-Secret
curl http://localhost:8082/api/products
# Expected: 403 Forbidden

# Always use the gateway instead:
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer <jwt_token>"
```

---

## 📦 Service Port Map

| Service | Port | Access | Database |
|---------|------|--------|----------|
| Eureka | 8761 | Direct | N/A |
| Gateway | 8080 | External (JWT verified here) | N/A |
| Auth | 8081 | Internal (blocked w/o gateway secret) | auth_db |
| Product | 8082 | Internal (blocked w/o gateway secret) | product_db |
| Inventory | 8083 | Internal (blocked w/o gateway secret) | inventory_db |
| Order | 8085 | Internal (blocked w/o gateway secret) | order_db |
| Payment | 8086 | Internal (blocked w/o gateway secret) | payment_db |
| Notification | 8087 | Internal (blocked w/o gateway secret) | notification_db |

---

## 🔧 Feign Client Usage

### In Order Service
```java
import com.ecommerce.order_service.client.InventoryClient;
import com.ecommerce.order_service.client.PaymentClient;
import com.ecommerce.order_service.client.CartClient;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final CartClient cartClient;
    
    // Constructor injection
    public OrderController(InventoryClient inventoryClient, 
                          PaymentClient paymentClient,
                          CartClient cartClient) {
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
        this.cartClient = cartClient;
    }
    
    @PostMapping
    public OrderResponse placeOrder(@RequestBody PlaceOrderRequest request) {
        // Call via Feign - Eureka handles routing automatically
        var reservation = inventoryClient.reserve(
            new InventoryClient.InventoryReservationRequest(...)
        );
        
        var payment = paymentClient.processPayment(
            new PaymentClient.ProcessPaymentRequest(...)
        );
        
        cartClient.clearCart(authHeader);
        
        return new OrderResponse(...);
    }
}
```

---

## ✅ Configuration Checklist

### For Each New Service/Modification

- [ ] Service name set in `spring.application.name`
- [ ] Port configured in `server.port`
- [ ] Eureka URL correct: `http://localhost:8761/eureka/`
- [ ] `eureka.instance.prefer-ip-address: true`
- [ ] Eureka lease settings configured
- [ ] Database configured with separate database per service
- [ ] For service-to-service: Feign client uses `name="service-name"`
- [ ] No hardcoded URLs in Feign clients
- [ ] Service can be reached through gateway
- [ ] Health endpoint accessible: `GET /health`

---

## 🐛 Troubleshooting

### Service shows as DOWN in Eureka
```bash
# Check logs
docker logs order-service

# Check if running
docker ps | grep order

# Verify Eureka URL
cat order-service/src/main/resources/application.yml | grep defaultZone
```

### Gateway returns 503 Service Unavailable
```bash
# Check Eureka has service registered
curl http://localhost:8761/eureka/apps/product-service

# Check service health
docker logs product-service | grep health

# Verify service registration
docker logs product-service | grep "Registering"
```

### Cannot call Feign client
```bash
# Verify:
1. Service has @EnableFeignClients
2. pom.xml has spring-cloud-starter-openfeign
3. Feign client uses name= not url=
4. Eureka running and service registered
```

### Services can't communicate
```bash
# In Docker - services use container names
http://inventory-service:8083  # ✅ Correct (container network)
http://localhost:8083          # ❌ Wrong (localhost not in container)

# Feign handles this automatically via Eureka
# Just use @FeignClient(name = "inventory-service")
```

---

## 🔐 Important Security Points

### Gateway Trust Model

The API Gateway is the **sole point of JWT verification**. Downstream services trust the gateway.

**Gateway (port 8080):**
- Verifies RS256 JWT tokens using RSA public key
- Extracts `userId` (subject), `email`, `roles` from JWT claims
- Injects headers: `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Gateway-Secret`
- Returns 401 if token missing or invalid
- Public routes (no token): `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`

**Downstream services (e.g., Product-Service):**
- **No JWT/RS256 verification** — removed
- Validates `X-Gateway-Secret` — returns 403 if missing/wrong
- Builds SecurityContext from `X-User-Id` and `X-User-Roles` headers
- Enforces role-based authorization (e.g., ADMIN only for POST/PUT/DELETE on products)

**Gateway Secret:**
- Configured via `GATEWAY_INTERNAL_SECRET` environment variable
- Direct access to backend ports (e.g., 8082) without this secret returns 403

### ✅ DO
```java
// ✅ Use service names
@FeignClient(name = "inventory-service")
interface InventoryClient { }

// ✅ Route through gateway
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer <jwt_token>"

// ✅ Let Eureka handle routing
// (automatically uses load balancing)

// ✅ Validate X-Gateway-Secret in downstream services
// Build SecurityContext from X-User-Id and X-User-Roles
```

### ❌ DON'T
```java
// ❌ Hardcode URLs
@FeignClient(url = "http://localhost:8083")
interface InventoryClient { }

// ❌ Direct access to services (bypasses security, returns 403)
curl http://localhost:8082/api/products

// ❌ Bypass Eureka
RestTemplate template = new RestTemplate();
template.getForObject("http://localhost:8083/...", String.class);

// ❌ Verify JWT in downstream services (gateway already did this)
// Downstream services should only validate X-Gateway-Secret
```

---

## 📈 Monitoring Commands

### View Service Status
```bash
# All services
curl http://localhost:8761/eureka/apps/ | jq

# Specific service
curl http://localhost:8761/eureka/apps/product-service | jq

# Service instance
curl http://localhost:8761/eureka/apps/product-service/product-service:8082 | jq
```

### Test Service Health
```bash
# Gateway health
curl http://localhost:8080/health

# Eureka health
curl http://localhost:8761/health

# Direct service health (local dev only)
curl http://localhost:8081/health
curl http://localhost:8082/health
```

---

## 📝 Common Implementation Pattern

### Step 1: Create Endpoints
```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        // Implementation
    }
}
```

### Step 2: From Another Service, Use Feign
```java
@FeignClient(name = "product-service")
public interface ProductClient {
    
    @GetMapping("/api/products/{id}")
    ProductResponse getProduct(@PathVariable("id") String id);
}
```

### Step 3: Inject and Use
```java
@RestController
public class OrderController {
    
    private final ProductClient productClient;
    
    public OrderController(ProductClient productClient) {
        this.productClient = productClient;
    }
    
    @PostMapping("/orders")
    public void placeOrder(PlaceOrderRequest request) {
        // Feign automatically routes via Eureka
        var product = productClient.getProduct(request.getProductId());
        // Continue order processing
    }
}
```

---

## 🚦 Service Startup Order

**In local development** (when services are independent):

1. **eureka-server** (start first, on port 8761)
2. **api-gateway** (on port 8080)
3. **all other services** (in any order)

Services register with Eureka within ~10 seconds of startup.

---

## 🔄 Service Restart Guide

### Restart Single Service
```bash
cd order-service && mvn spring-boot:run
# Service re-registers in ~10 seconds
```

### Restart Eureka (Careful!)
```bash
# Stop Eureka Server
# Restart it: cd eureka-server && mvn spring-boot:run
# All services re-register in ~30 seconds
# Gateway starts returning 503 until services re-register
```

### Restart Everything
```bash
# Stop all, then restart in order
# Wait 30-45 seconds for all services to register
```

---

## 📖 Documentation Files

| File | Purpose | Audience |
|------|---------|----------|
| `docs/architecture/IMPLEMENTATION_SUMMARY.md` | Overview of all changes | Tech Leads |
| `docs/architecture/MICROSERVICES_GUIDE.md` | Detailed architecture guide | Architects |
| `docs/developer/QUICK_START.md` | Testing & validation guide | QA / DevOps |
| `docs/developer/DEVELOPER_REFERENCE.md` | This file - quick lookup | Developers |

---

## 🎓 Learning Resources

### Spring Cloud Documentation
- [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- [Spring Cloud Netflix Eureka](https://spring.io/projects/spring-cloud-netflix)
- [Spring Cloud OpenFeign](https://spring.io/projects/spring-cloud-openfeign)

### Project Documentation
- See `spec-kit/tasks/tasks.md` for detailed task breakdown
- See `docs/architecture/MICROSERVICES_GUIDE.md` for architecture decisions
- See `docs/developer/QUICK_START.md` for testing procedures

---

## 💬 Quick Help

| Problem | Solution | Reference |
|---------|----------|-----------|
| Service shows DOWN | Check logs, verify Eureka URL | Troubleshooting section |
| Gateway returns 503 | Verify service registered in Eureka | Monitoring Commands |
| Can't call Feign client | Ensure @EnableFeignClients and right dependency | Configuration Checklist |
| Database connection fails | Check database credentials and connection URL | Port Map, Database per Service |
| Services can't communicate | Use Eureka, not hardcoded URLs | Common Implementation Pattern |

---

**Last Updated**: May 1, 2026
**Version**: 1.0
**Status**: Ready for Use

