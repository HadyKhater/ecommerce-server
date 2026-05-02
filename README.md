# E-Commerce Microservices Platform

A Spring Boot microservices platform with Eureka-based service discovery and centralized API Gateway.

## Quick Start

```bash
# Start all services
./mvnw spring-boot:run -pl eureka-server
./mvnw spring-boot:run -pl api-gateway
./mvnw spring-boot:run -pl auth-service,product-service,inventory-service,order-service,payment-service,notification-service
```

## Architecture

| Service | Port | Description |
|---------|------|-------------|
| eureka-server | 8761 | Service discovery |
| api-gateway | 8080 | API Gateway (single entry point) |
| auth-service | 8081 | Authentication & authorization |
| product-service | 8082 | Product catalog |
| inventory-service | 8083 | Inventory management |
| order-service | 8084 | Order processing |
| payment-service | 8085 | Payment handling |
| notification-service | 8086 | Notifications |

## Documentation

- **[Quick Start](docs/QUICK_START.md)** - Get running in minutes
- **[Developer Reference](docs/DEVELOPER_REFERENCE.md)** - Commands, ports, troubleshooting
- **[Architecture Guide](docs/architecture/MICROSERVICES_GUIDE.md)** - Detailed architecture
- **[Implementation Summary](docs/architecture/IMPLEMENTATION_SUMMARY.md)** - What was built
- **[Verification Checklist](docs/architecture/VERIFICATION_CHECKLIST.md)** - Complete feature list

## Project Structure

```
ecommerce-server/
├── api-gateway/          # API Gateway (port 8080)
├── auth-service/         # Authentication service (port 8081)
├── eureka-server/        # Service discovery (port 8761)
├── product-service/      # Product catalog (port 8082)
├── inventory-service/    # Inventory management (port 8083)
├── order-service/        # Order processing (port 8084)
├── payment-service/      # Payment handling (port 8085)
├── notification-service/ # Notifications (port 8086)
├── docs/                 # Documentation
│   ├── architecture/     # Architecture docs
│   ├── developer/        # Developer guides
│   └── README_MICROSERVICES.md
├── spec-kit/             # Specification kit
└── pom.xml               # Parent POM
```

## Requirements

- Java 17+
- Maven 3.8+
- Docker (optional, for containerized deployment)

## License

MIT
