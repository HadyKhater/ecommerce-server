# Features Added Beyond the Original Brief — WHAT_WAS_ADDED.md

This document records the advanced features and architectural optimizations implemented in the E-Commerce platform that extend beyond the baseline requirements of the original project specification.

---

## 1. Full Stripe Payment Gateway & Webhook Integration

While the brief requested a payment simulator, the platform implements a production-ready **Stripe Payment Gateway** integration alongside the simulated mock process.

### How it Works
1. **Checkout Session Creation:**
   - Endpoint: `POST /stripe/checkout` (handled by `StripeController`).
   - The frontend calls this endpoint with the order metadata, currency, line items, and any promo code.
   - The `StripeServiceImpl` initializes the Stripe Java SDK (using the secure API key loaded via `StripeProperties`).
   - It builds a Stripe `SessionCreateParams` payload, registering the order and user IDs as transaction metadata.
   - It invokes `Session.create()` to generate a secure checkout URL hosted on Stripe's domains, returning this checkout URL back to the client.
2. **Promotional Coupons Mapping:**
   - If a discount code is active, the `StripeCouponService` automatically registers a temporary coupon inside Stripe matching the discount type (percentage or fixed amount) and value before creating the session.
3. **Secure Webhook Callback:**
   - Endpoint: `POST /stripe/webhook`.
   - Stripe calls this endpoint asynchronously when a checkout completes successfully.
   - **Signature Verification:** The endpoint verifies the payload authenticity by validating the `Stripe-Signature` header against the local `webhookSecret`.
   - **Deserialization and Execution:** On verification, it extracts the event type (`checkout.session.completed`). It parses the raw session JSON to extract the custom `orderId` metadata.
   - It invokes the dynamic `orderServiceClient.confirmOrder(orderId)` Feign client call to move the order status from `RESERVED` to `CONFIRMED`.

---

## 2. In-Memory Catalog Caching (Caffeine)

Under high catalog traffic, repetitive database lookups for product searches and single-item fetches create unnecessary database strain. The `Product Service` implements a dynamic caching layer using **Caffeine**.

### Implementation Details
- **Configuration:** Initialized in `CacheConfig.java` with a 15-minute write-based Time-To-Live (TTL):
  ```java
  Caffeine.newBuilder()
          .initialCapacity(100)
          .maximumSize(1000)
          .expireAfterWrite(Duration.ofMinutes(15))
          .recordStats();
  ```
- **Caching Rules:**
  - Product Searches: `@Cacheable(value = "product-search", key = "#request.hashCode()")` caches catalog searches by the hash codes of their search filter requests.
  - Single Items: `@Cacheable(value = "products", key = "#id")` caches individual products by UUID.
  - Active Listings: `@Cacheable(value = "all-products")` caches admin-level product lists.
- **Eviction Strategy:** To prevent dirty reads, any product adjustments, creations, deletions, or image adjustments immediately evict all product cache categories:
  ```java
  @CacheEvict(value = {"products", "product-search", "all-products"}, allEntries = true)
  ```

---

## 3. Distributed Request Tracing (Correlation ID)

To troubleshoot issues in a distributed microservices environment, developers must be able to trace a single request as it flows across multiple service boundaries. The platform includes a **Correlation ID Request Tracing** filter system.

### How it Works
1. **Edge Gateway Filter:**
   - The API Gateway runs a global `CorrelationIdFilter` (`GlobalFilter`) configured at the highest precedence.
   - It checks inbound requests for an `X-Correlation-Id` header. If missing, it generates a fresh UUID.
   - It mutates the HTTP request to inject `X-Correlation-Id` before routing downstream, and appends it to the outbound client response.
2. **Downstream Servlet Filter:**
   - Downstream services (e.g., `cart-service`) run a matching `CorrelationIdFilter` (`Filter`).
   - The filter intercepts the HTTP call, extracts the `X-Correlation-Id` header, and populates the **SLF4J Mapped Diagnostic Context (MDC)** under the key `correlationId`.
3. **Structured Log Traceability:**
   - The logging framework is configured to print `%X{correlationId}` on every line.
   - If an error occurs in `inventory-service` during checkout, developers can search log aggregators for that specific `correlationId` to trace the entire user request path starting from the API Gateway down to the repository queries.

---

## 4. Homepage Banners & Slide Management

To provide a dynamic dashboard interface for landing pages, the `Product Service` includes landing sliders controlled via administrative endpoints.

### How it Works
- **Management:** Administrators use `/banners` CRUD operations (`BannerController`) to create, update, delete, or re-order image banners.
- **Status Toggling:** Banners include active/inactive statuses. Administrators can toggle them instantly via `PATCH /banners/{id}/toggle`.
- **Public Fetching:** The homepage carousel loads banners using `GET /banners/public`, which bypasses authorization requirements and retrieves only active banners.

---

## 5. Advanced Category Slugs & Activation Toggles

The platform implements an advanced category management system to support search engine friendly URLs and safe administrative controls.

### How it Works
- **Dynamic Slug Generation:** When a category is created, the system uses a custom `SlugUtil` helper class to convert category titles into clean, lowercase, hyphenated slugs (e.g., `"Home Electronics & Gadgets"` becomes `"home-electronics-gadgets"`).
- **Collision Resolution:** If a duplicate slug name is detected, the utility automatically appends unique numeric suffix counters to resolve database uniqueness constraints.
- **Deletion Safeguards:** When an administrator attempts to delete a category via `DELETE /categories/{id}`, the service checks the `product-db` to verify if any products are assigned to the category. If products exist, the deletion is rejected with an `IllegalArgumentException`, protecting catalog relationships.
- **Hierarchical Toggle:** Administrators can deactivate category trees using `PATCH /categories/{id}/toggle-active`. Deactivated categories are hidden from the public product list.
