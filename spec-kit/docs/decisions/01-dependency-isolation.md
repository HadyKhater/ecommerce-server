# Dependency Isolation Fix

## Why Parent POM Shouldn't Have Database Dependencies

The parent POM should only contain dependencies that are truly common across ALL microservices. Database-related dependencies like `spring-boot-starter-data-jpa` and `postgresql` should not be inherited by services that don't need them (e.g., eureka-server, api-gateway), as this causes Spring Boot to attempt auto-configuration of DataSource and JPA, leading to "Failed to configure DataSource" errors.

## Services Needing JPA vs Not Needing

### Services WITHOUT JPA (no database):
- **eureka-server**: Discovery server, no data persistence needed
- **api-gateway**: Routing proxy, no data persistence needed

### Services WITH JPA (require database):
- **auth-service**: User authentication and sessions
- **product-service**: Product catalog management
- **inventory-service**: Stock and inventory tracking
- **order-service**: Order processing and history
- **payment-service**: Payment processing and records
- **notification-service**: Notification logs and templates

## How to Verify the Fix

1. **Build the project**: `mvn clean compile`
2. **Start eureka-server**: `mvn spring-boot:run -pl eureka-server` (should start on port 8761 without DataSource errors)
3. **Start api-gateway**: `mvn spring-boot:run -pl api-gateway` (should start on port 8080 without DataSource errors)
4. **Check health endpoints**:
   - Eureka: http://localhost:8761 (should show Eureka dashboard)
   - API Gateway: http://localhost:8080/health (should return "API Gateway is healthy")
5. **Verify no database-related logs**: Look for absence of DataSource/Hibernate initialization messages in startup logs</content>
<parameter name="filePath">spec-kit/docs/tasks/01-dependency-isolation.md
