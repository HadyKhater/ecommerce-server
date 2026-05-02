# Implementation Guide: E-Commerce Platform

## Overview

This guide provides practical instructions for developers implementing the e-commerce platform. It covers setup, development patterns, testing approach, and deployment procedures.

**Target Audience:** Backend developers implementing services  
**Prerequisites:** Java 21, Maven, PostgreSQL local, basic Spring Boot knowledge  
**Last Updated:** April 2026

---

## Scope

This guide covers:
- Local development environment setup
- Project creation and structure
- Common implementation patterns
- Testing procedures
- Troubleshooting tips

Out of scope:
- Frontend development
- CI/CD pipeline setup
- Production deployment
- Monitoring and alerting

---

## Part 1: Local Development Setup

### Prerequisites

Before starting, verify you have:

```
Java: java -version  
→ Java 21.x or higher

Maven: mvn -version  
→ Maven 3.8.x or higher

PostgreSQL: psql --version  
→ PostgreSQL 14+ running locally

Git: git --version  
→ For version control
```

### Step 1: Install PostgreSQL (Windows)

1. Download from https://www.postgresql.org/download/windows/
2. Install with default settings
3. Note the password for `postgres` user (default: `postgres`)
4. Verify: Open pgAdmin4 at http://localhost:5050

### Step 2: Create Databases

Open pgAdmin4 and create 7 new databases:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE cart_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
-- Notification service has no database
```

### Step 3: Configure Maven

Create/update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <configuration>
        <timeout>10000</timeout>
      </configuration>
    </server>
  </servers>
</settings>
```

### Step 4: Create Root Project

Create root Maven project structure:

```
ecommerce-platform/
├── pom.xml (parent)
├── eureka-server/
│   └── pom.xml
├── api-gateway/
│   └── pom.xml
├── auth-service/
│   └── pom.xml
├── product-service/
│   └── pom.xml
├── inventory-service/
│   └── pom.xml
├── cart-service/
│   └── pom.xml
├── order-service/
│   └── pom.xml
├── payment-service/
│   └── pom.xml
└── notification-service/
    └── pom.xml
```

#### Root pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.ecommerce</groupId>
  <artifactId>ecommerce-platform</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>auth-service</module>
    <module>product-service</module>
    <module>inventory-service</module>
    <module>cart-service</module>
    <module>order-service</module>
    <module>payment-service</module>
    <module>notification-service</module>
  </modules>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
    <relativePath/>
  </parent>

  <properties>
    <java.version>21</java.version>
    <spring-cloud.version>2024.0.0</spring-cloud.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <resilience4j.version>2.1.0</resilience4j.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-bom</artifactId>
        <version>${resilience4j.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Common dependencies for all services -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

### Step 5: Create Service Template

For each service, use this child pom.xml template:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.ecommerce</groupId>
    <artifactId>ecommerce-platform</artifactId>
    <version>1.0.0</version>
  </parent>

  <artifactId>auth-service</artifactId>
  <name>auth-service</name>

  <dependencies>
    <!-- Web & Security -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Migration -->
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>

    <!-- JWT -->
    <dependency>
      <groupId>com.auth0</groupId>
      <artifactId>java-jwt</artifactId>
      <version>4.4.0</version>
    </dependency>

    <!-- Microservices -->
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <!-- MapStruct -->
    <dependency>
      <groupId>org.mapstruct</groupId>
      <artifactId>mapstruct</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>

    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers</artifactId>
      <version>1.20.0</version>
      <scope>test</scope>
    </dependency>

    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <version>1.20.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>org.mapstruct</groupId>
              <artifactId>mapstruct-processor</artifactId>
              <version>${mapstruct.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## Part 2: Common Implementation Patterns

### Pattern 1: Service Interface & Implementation

**File:** `com.ecommerce.productservice/service/IProductService.java`

```java
public interface IProductService {
    ProductResponse getProduct(UUID id);
    Page<ProductResponse> searchProducts(ProductSearchRequest request);
    ProductResponse createProduct(CreateProductRequest request);
    ProductResponse updateProduct(UUID id, UpdateProductRequest request);
    void deleteProduct(UUID id);
}
```

**File:** `com.ecommerce.productservice/service/ProductService.java`

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService implements IProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "product-cache", key = "#id")
    public ProductResponse getProduct(UUID id) {
        return repository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @CacheEvict(value = "product-cache", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = mapper.toEntity(request);
        Product saved = repository.save(product);
        return mapper.toResponse(saved);
    }
}
```

### Pattern 2: DTO as Java Records

```java
public record ProductCreateRequest(
    String name,
    String description,
    @DecimalMin("0.01") BigDecimal price,
    @NotNull UUID categoryId,
    @Min(0) Integer stock,
    String imageUrl
) {}

public record ProductResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String category,
    Integer stock,
    String imageUrl,
    Instant createdAt,
    Instant updatedAt
) {}
```

### Pattern 3: Optimistic Locking for Inventory

```java
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    private UUID productId;
    
    private int totalStock;
    private int reservedStock;
    
    @Version
    private long version;
    
    public void reserve(int quantity) {
        int available = totalStock - reservedStock;
        if (available < quantity) {
            throw new InsufficientStockException(
                "Need %d units, only %d available".formatted(quantity, available)
            );
        }
        this.reservedStock += quantity;
    }
}
```

**Retry Logic in Service:**

```java
@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final int MAX_RETRIES = 3;
    private static final int[] BACKOFF_MS = {10, 20, 40};

    public ReservationResponse reserve(ReservationRequest request) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return attemptReservation(request);
            } catch (OptimisticLockingFailureException e) {
                if (attempt < MAX_RETRIES - 1) {
                    sleep(BACKOFF_MS[attempt]);
                    continue;
                }
                throw new InsufficientStockException("Out of stock after retries");
            }
        }
        throw new RuntimeException("Unexpected state");
    }

    @Transactional
    private ReservationResponse attemptReservation(ReservationRequest request) {
        Inventory inventory = repository.findByProductId(request.productId())
            .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        
        inventory.reserve(request.quantity());
        Inventory saved = repository.save(inventory);
        
        return new ReservationResponse(saved.getProductId(), request.quantity());
    }
}
```

### Pattern 4: Idempotent Operations

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;
    
    @Index(name = "idx_idempotency_key")
    private String idempotencyKey;
    
    // ... other fields
}

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository repository;
    
    public OrderResponse placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        // Check if exists
        Optional<Order> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return mapper.toResponse(existing.get());
        }
        
        // Create new
        Order order = createOrder(request);
        order.setIdempotencyKey(idempotencyKey);
        
        Order saved = repository.save(order);
        return mapper.toResponse(saved);
    }
}
```

### Pattern 5: Domain Events

```java
// Event class
@Getter
public class OrderPaidEvent extends ApplicationEvent {
    private final UUID orderId;
    private final UUID userId;
    private final BigDecimal amount;
    
    public OrderPaidEvent(Object source, UUID orderId, UUID userId, BigDecimal amount) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }
}

// Publishing
@Service
@RequiredArgsConstructor
public class OrderService {
    private final ApplicationEventPublisher publisher;
    
    @Transactional
    public void confirmPayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow();
        
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        
        publisher.publishEvent(new OrderPaidEvent(
            this, order.getId(), order.getUserId(), order.getTotalPrice()
        ));
    }
}

// Listening
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final IEmailService emailService;
    
    @EventListener
    public void onOrderPaid(OrderPaidEvent event) {
        EmailMessage message = EmailMessage.builder()
            .to(getUserEmail(event.getUserId()))
            .subject("Order %s Confirmed".formatted(event.getOrderId()))
            .body("Your payment of $%.2f has been received.".formatted(event.getAmount()))
            .build();
        
        emailService.send(message);
    }
}
```

### Pattern 6: Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleProductNotFound(
            ProductNotFoundException e, HttpServletRequest request) {
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Product not found: " + e.getMessage()
        );
        problem.setType(URI.create("https://api.example.com/errors/product-not-found"));
        problem.setTitle("Product Not Found");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        
        Map<String, String> errors = e.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setProperty("errors", errors);
        
        return ResponseEntity.badRequest().body(problem);
    }
}
```

### Pattern 7: Controller with Thin Logic

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final IProductService service;
    
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> search(
            @Valid ProductSearchQuery query) {
        ProductSearchRequest request = new ProductSearchRequest(
            query.keyword(),
            query.category(),
            query.minPrice(),
            query.maxPrice(),
            query.page(),
            query.size()
        );
        return ResponseEntity.ok(service.searchProducts(request));
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = service.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

## Part 3: Testing Approach

### Unit Tests using Mockito

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    ProductRepository repository;
    
    @Mock
    ProductMapper mapper;
    
    @InjectMocks
    ProductService service;
    
    @Test
    void testGetProduct_ReturnsProduct() {
        // Arrange
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(id).name("Test").build();
        ProductResponse response = new ProductResponse(id, "Test", "", BigDecimal.TEN, ...);
        
        when(repository.findById(id)).thenReturn(Optional.of(product));
        when(mapper.toResponse(product)).thenReturn(response);
        
        // Act
        ProductResponse result = service.getProduct(id);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        verify(repository).findById(id);
    }
    
    @Test
    void testGetProduct_NotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        
        assertThrows(ProductNotFoundException.class, () -> service.getProduct(id));
    }
}
```

### Integration Tests with Testcontainers

```java
@SpringBootTest
@Testcontainers
class OrderServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("order_test_db")
            .withUsername("postgres")
            .withPassword("postgres");
    
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    OrderRepository repository;
    
    @Autowired
    OrderService service;
    
    @Test
    void testPlaceOrder_Success() throws Exception {
        // Real database test
        UUID userId = UUID.randomUUID();
        PlaceOrderRequest request = new PlaceOrderRequest(...);
        
        OrderResponse response = service.placeOrder(request);
        
        assertThat(response.id()).isNotNull();
        Order saved = repository.findById(response.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

### Concurrency Tests for Inventory

```java
@SpringBootTest
@Testcontainers
class InventoryConcurrencyTest {
    @Container
    static PostgreSQLContainer<?> postgres = ...;
    
    @Autowired
    InventoryRepository repository;
    
    @Autowired
    InventoryService service;
    
    @Test
    void testConcurrentReservations_NoOverselling() throws Exception {
        // Setup: 50 units in stock
        UUID productId = UUID.randomUUID();
        Inventory inventory = new Inventory(productId, 50, 0, 0);
        repository.save(inventory);
        
        // Execute: 100 concurrent reservation attempts
        ExecutorService executor = Executors.newFixedThreadPool(100);
        int successCount = new AtomicInteger(0);
        int failureCount = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(100);
        
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    ReservationRequest request = new ReservationRequest(productId, 1);
                    service.reserve(request);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Assert: Exactly 50 successes, 50 failures
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failureCount.get()).isEqualTo(50);
        
        Inventory final_state = repository.findById(productId).orElseThrow();
        assertThat(final_state.getReservedStock()).isEqualTo(50);
        assertThat(final_state.getTotalStock()).isEqualTo(50);
    }
}
```

---

## Part 4: Running Services Locally

### Startup Order

1. **PostgreSQL** (already running)
2. **Eureka Server** `:8761`

```bash
cd eureka-server
mvn spring-boot:run
```

3. **API Gateway** `:8080`

```bash
cd api-gateway
mvn spring-boot:run
```

4. **Auth Service** `:8081`

```bash
cd auth-service
mvn spring-boot:run
```

5. **Other Services** (parallel)

```bash
# Terminal 1: Product Service
cd product-service && mvn spring-boot:run

# Terminal 2: Inventory Service
cd inventory-service && mvn spring-boot:run

# Terminal 3: Cart Service
cd cart-service && mvn spring-boot:run

# Terminal 4: Order Service
cd order-service && mvn spring-boot:run

# Terminal 5: Payment Service
cd payment-service && mvn spring-boot:run

# Terminal 6: Notification Service
cd notification-service && mvn spring-boot:run
```

### Verify All Services Running

```bash
curl http://localhost:8761/eureka/apps

# Should show all services registered
```

### Test Basic Flow

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "Password123",
    "fullName": "John Doe"
  }'

# 2. Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "Password123"
  }'
# Save accessToken

# 3. List Products
curl http://localhost:8080/api/products \
  -H "Authorization: Bearer {accessToken}"

# 4. Add to Cart
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "...",
    "quantity": 1
  }'
```

---

## Part 5: Troubleshooting

### PostgreSQL Connection Refused

**Problem:** `FATAL: password authentication failed for user "postgres"`

**Solution:**
1. Verify PostgreSQL running: `psql -U postgres -c "SELECT 1"`
2. Check default password: `postgres`
3. Update connection string with correct password

### Eureka Not Registering Services

**Problem:** Service shows "Connection refused" in Eureka dashboard

**Solution:**
1. Verify Eureka running: `curl http://localhost:8761`
2. Check service `application.yaml` has Eureka config
3. Look for startup errors in service logs

### Flyway Migration Failed

**Problem:** `Flyway migration error: schema validation failed`

**Solution:**
1. Drop database: `dropdb {db_name}` (loses data!)
2. Recreate empty database: `createdb {db_name}`
3. Restart service to re-run migrations

### Optimistic Lock Exception

**Problem:** `org.springframework.orm.ObjectOptimisticLockingFailureException`

**Solution:**
1. Expected under concurrent load
2. Service should automatically retry
3. Check retry logic in service layer
4. Verify `@Version` annotation on entity

### JWT Token Expired

**Problem:** `401 Unauthorized` on protected endpoints

**Solution:**
1. Use refresh token: `POST /api/auth/refresh`
2. Or login again: `POST /api/auth/login`
3. Check token expiry time (15 minutes)

---

## Part 6: Development Workflow

### Daily Development Cycle

1. **Plan:** Check tasks list for your sprint

2. **Create Feature Branch:**
```bash
git checkout -b feature/inventory-reserve
```

3. **Implement:** Write code + tests

4. **Run Tests Locally:**
```bash
cd inventory-service
mvn clean test
```

5. **Start Service Locally:**
```bash
mvn spring-boot:run
```

6. **Manual Test via curl/Postman**

7. **Commit & Push:**
```bash
git add .
git commit -m "feat: add inventory reservation with optimistic locking"
git push origin feature/inventory-reserve
```

8. **Create Pull Request:** For peer review

---

## Part 7: Configuration Files

### application.yaml Template (All Services)

```yaml
spring:
  application:
    name: {service-name}
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-create, use Flyway
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
  datasource:
    url: jdbc:postgresql://localhost:5432/{db_name}
    username: postgres
    password: postgres
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  flyway:
    enabled: true
    locations: classpath:db/migration
    baselineOnMigrate: true

server:
  port: {port}
  servlet:
    context-path: /

eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    instance-id: ${spring.application.name}:${server.port}

logging:
  level:
    root: INFO
    com.ecommerce: DEBUG
    org.springframework.cloud: INFO
  pattern:
    console: "%d{ISO8601} %5p %40.40c{1.} : %m%n"

# Resilience4j configuration (for services calling other services)
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureThresholdPercentage: 50
        waitDurationInOpenState: 10000
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 100
```

---

## Part 8: Security Best Practices

### Never Commit Secrets

```
# .gitignore
*.p12
*.jks
keystore.*
.env
application-prod.yaml
```

### Use Environment Variables

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}
    
jwt:
  secret: ${JWT_SECRET}
```

### Run Tests Before Commit

```bash
mvn clean test
```

---

## Part 9: Code Review Checklist

Before creating a pull request, verify:

- [ ] Code follows package structure
- [ ] Service interfaces exist for all public methods
- [ ] DTOs use Java records
- [ ] No `@Autowired` field injection (use constructor only)
- [ ] Exception handling with specific exceptions
- [ ] No magic strings (use constants/enums)
- [ ] Input validation at controller
- [ ] Business logic at service layer
- [ ] Unit tests written (>80% coverage on service layer)
- [ ] Integration tests for DB operations
- [ ] Flyway migration created for schema changes
- [ ] No `System.out.println()` (use logging)
- [ ] Global exception handler catches all errors

---

## Part 10: Common Gotchas

### Issue 1: Circular Dependencies

**Problem:** `BeanCurrentlyInCreationException`

**Solution:** Use constructor injection, avoid circular references

### Issue 2: Transaction Not Starting

**Problem:** Changes not saved to database

**Solution:**
1. Verify `@Transactional` on service method
2. Verify `spring-boot-starter-data-jpa` dependency added
3. Check `ddl-auto: validate` (not `create`)

### Issue 3: Lazy Loading Error

**Problem:** `LazyInitializationException: could not initialize proxy`

**Solution:**
1. Use `@Transactional` on service method (for lazy load)
2. Or fetch eagerly with `@Fetch(FetchMode.JOIN)`
3. Or call `.size()` on collection while still in transaction

### Issue 4: Multiple JWT Instances

**Problem:** Different tokens generated for same user

**Solution:** Store private key securely, generate from single location only

---

## Resources

- **Spring Boot Docs:** https://spring.io/projects/spring-boot
- **Spring Cloud:** https://spring.io/projects/spring-cloud
- **PostgreSQL Docs:** https://www.postgresql.org/docs/
- **JWT Introduction:** https://jwt.io/
- **Resilience4j:** https://resilience4j.readme.io/docs


