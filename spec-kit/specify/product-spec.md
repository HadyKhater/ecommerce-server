# Product Specification: E-Commerce Platform

## Overview

This document specifies the functional requirements and feature set for a high-traffic e-commerce platform. It defines all user-facing features, system capabilities, and product behavior.

**Platform Type:** B2C E-Commerce  
**Primary User:** Consumers making online purchases  
**Admin Users:** Platform operators and product managers  
**Last Updated:** April 2026

---

## Scope

This specification covers:

- Product discovery and catalog management
- User authentication and account management
- Shopping cart operations
- Order processing and fulfillment
- Payment processing
- Inventory management and stock tracking
- Real-time notifications

Out of scope:
- Physical logistics integration
- Real payment provider integration
- Customer support ticketing
- Analytics and reporting

---

## Feature 1: Product Catalog

### Overview

Users can browse, search, and discover products. Admins manage the complete product catalog.

### Product Information

Each product contains:

- **Name** (required, searchable)
- **Description** (required, searchable)
- **Price** (decimal, required)
- **Category** (required, filterable)
- **Stock Quantity** (integer, tracked)
- **Image URL** (optional)
- **Created At** (timestamp, system-managed)
- **Updated At** (timestamp, system-managed)

### Search & Discovery

#### Search by Keyword
- Search across **product name** and **description**
- Case-insensitive fuzzy matching
- Results sorted by relevance

#### Filtering
- **By Category:** Multi-select category filter
- **By Price Range:** Min and max price sliders
- **By Availability:** Show in-stock products only
- **Combined Filtering:** All filters work together

#### Pagination
- Configurable page size (10, 25, 50 products)
- Sequential page numbers
- Total count provided
- Default: 25 items per page

### Admin Operations

#### Create Product
- Required fields: name, description, price, category, stock
- Optional fields: image URL
- Validation: price > 0, stock ≥ 0
- Response: Created product with UUID

#### Update Product
- Admin can modify any product field
- Update existing image or upload new
- Stock adjustments synchronized with inventory service
- Validation: same as create

#### Delete Product
- Soft delete (mark as deleted, don't remove)
- Product no longer appears in search results
- Order history preserved

---

## Feature 2: User Management

### Authentication

#### Registration
- **Input:** Email, password, full name
- **Validation:**
  - Email must be unique and valid format
  - Password minimum 8 characters (1 uppercase, 1 lowercase, 1 digit)
  - Name minimum 2 characters
- **Response:** User created with UUID, initial USER role
- **Error:** Duplicate email returns 409 Conflict

#### Login
- **Input:** Email, password
- **Validation:**
  - Credentials verified against BCrypt hash
  - Account must be active
- **Response:** Access token (JWT 15 min) + Refresh token (7 days)
- **Error:** Invalid credentials return 401 Unauthorized

#### Refresh Token
- **Input:** Refresh token
- **Validation:** Token not expired, signature valid
- **Response:** New access token
- **Error:** Invalid/expired token returns 401 Unauthorized

### User Profile

#### Profile Information

Each user has:
- **ID** (UUID)
- **Email** (unique)
- **Full Name**
- **Addresses** (list of address objects)
- **Role** (USER or ADMIN)
- **Created At** (timestamp)
- **Updated At** (timestamp)

#### Address Management

Each address contains:
- **Line 1** (street address, required)
- **Line 2** (apartment/suite, optional)
- **City** (required)
- **State/Province** (required)
- **Postal Code** (required)
- **Country** (required)
- **Is Default** (boolean—one address marked as default for checkout)

#### Operations

- **Get Profile:** Users can view their own profile
- **Update Profile:** Change name, email, date of birth
- **Add Address:** Max 5 addresses per user
- **Update Address:** Modify any address
- **Delete Address:** Cannot delete if it's default
- **Set Default Address:** For checkout pre-fill

---

## Feature 3: Shopping Cart

### Cart Persistence

- **Storage:** Database (PostgreSQL)
- **Survival:** Cart persists across sessions and page refreshes
- **User Association:** One cart per authenticated user
- **Lifespan:** Cart retained for 30 days of inactivity

### Cart Operations

#### View Cart
- **Response:** List of cart items with:
  - Product ID and name
  - Unit price (current product price)
  - Quantity
  - Line total (unit price × quantity)
  - Product image URL
- **Subtotal:** Sum of all line totals
- **Empty Cart Status:** Clear message when cart is empty

#### Add to Cart
- **Input:** Product UUID, Quantity (default 1)
- **Validation:** Quantity ≥ 1, Product exists
- **Behavior:**
  - If product already in cart: increment quantity
  - If new product: add with quantity
- **Response:** Updated cart with item added
- **Error:** Product not found returns 404

#### Update Quantity
- **Input:** Product UUID, New quantity
- **Validation:** Quantity ≥ 1
- **Behavior:**
  - Quantity set to exact value
  - If quantity = 0: item removed from cart
- **Response:** Updated cart
- **Concurrency:** Handle concurrent modifications (same user, simultaneous requests)

#### Remove Item
- **Input:** Product UUID
- **Behavior:** Item deleted from cart
- **Response:** Updated cart
- **Edge Case:** Removing only item leaves empty cart

#### Clear Cart
- **Behavior:** All items removed
- **Response:** Empty cart confirmation

### Concurrent Safety

- **Race Condition:** Multiple simultaneous requests to update same cart
- **Solution:** Optimistic locking on cart record
- **Behavior:** Last-write-wins with conflict detection
- **Error Response:** 409 Conflict if concurrent modification detected, user must reload

---

## Feature 4: Order Processing

### Order Lifecycle

```
PENDING
  ↓ (customer reviews and confirms)
AWAITING_PAYMENT
  ↓ (payment processed)
PAID
  ↓ (warehouse prepares shipment)
SHIPPED
  ↓ (delivery in progress)
DELIVERED
```

Alternative flows:
```
AWAITING_PAYMENT → CANCELLED (payment failed or user cancels)
PENDING → CANCELLED (user cancels before payment)
```

### Place Order

#### Checkout Flow
1. **Validate Cart:** Cart must have ≥ 1 item
2. **Validate Inventory:** All items in stock (exact reservation happens in inventory service)
3. **Confirm Delivery Address:** User selects from saved addresses or enters new
4. **Review Order:** Display items, prices, subtotal, shipping (if applicable)
5. **Process Payment:** Payment service handles transaction
6. **Create Order:** On payment success, order created in PAID state

#### Order Creation
- **Input:**
  - Cart items (product ID, quantity)
  - Delivery address
  - Payment method
- **Idempotency:** Duplicate requests within 5 minutes return same order
  - Implementation: Idempotency-Key header matched to existing order
- **Response:** New order with:
  - Order UUID
  - Order number (human-readable, e.g., ORD-2026-001234)
  - Item details
  - Total price
  - Delivery address
  - Status
  - Created timestamp

### Order Operations

#### View Order
- **Scope:** Users see only their own orders; admins see all
- **Response:** Full order details including:
  - Items (product name, quantity, price, subtotal)
  - Delivery address
  - Status history (timeline of status changes)
  - Payment information (last 4 digits of card, timestamp)
  - Tracking number (once SHIPPED)

#### Order History
- **Filtering by Status:** USER, ADMIN roles can filter by:
  - PENDING, AWAITING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED
- **Pagination:** 10 orders per page, newest first
- **Response:** List of orders with status, date, total

#### Cancel Order
- **Eligibility:** Only if status is PENDING or AWAITING_PAYMENT
- **Effect:**
  - Status changes to CANCELLED
  - Inventory reservations released
  - Refund initiated (if paid)
- **Response:** Confirmation with cancellation timestamp
- **Error:** Cannot cancel PAID/SHIPPED/DELIVERED orders

### Order Notifications

Triggered automatically:
- **Order Confirmed:** When order enters PAID state
- **Payment Success:** Immediately after payment clears
- **Payment Failed:** If payment retries exhausted
- **Order Shipped:** When status changes to SHIPPED (admin updates)

---

## Feature 5: Payment Processing

### Payment Flow

#### Initiate Payment
- **Trigger:** User submits checkout (after cart validation)
- **Input:** Order ID, card details (mock: card number, CVV, expiry)
- **Timeout:** 30 seconds for payment processing

#### Mock Payment Provider
- **Configuration:** Configurable success rate (e.g., 95% to simulate real world)
- **Responses:**
  - **Success:** Payment authorized, returns transaction ID
  - **Failure:** Declined (insufficient funds, expired card, etc.) with error code

#### Retry Strategy
- **Automatic Retries:** Up to 3 attempts
- **Backoff:** Exponential (1 second, 2 seconds, 4 seconds between retries)
- **Final Failure:** After 3 retries, order marked as payment failed, cart not cleared
- **User Action:** User can retry manually from order page

#### Idempotency
- **Prevention:** Duplicate charges prevented via idempotency keys
- **Implementation:** Each payment attempt gets unique idempotency key
- **Retrieval:** Subsequent requests with same key return original result (no duplicate charge)

#### Payment Status
- **Response Status Codes:**
  - `200 OK`: Payment successful
  - `402 Payment Required`: Payment declined/failed
  - `408 Request Timeout`: Payment processing timeout (user can retry)

---

## Feature 6: Inventory Management (CRITICAL)

### Stock Tracking

Each product has two stock counts:
- **Total Stock:** Actual inventory count
- **Available Stock:** Total - Reserved (available for new orders)

### Reservation System

#### Reserve Stock (During Checkout)
- **Trigger:** When user places order, before payment
- **Operation:** Decrement available stock, add reservation record
- **Atomicity:** Single database transaction
- **Lock:** Optimistic locking on Product row using `@Version`

#### Confirm Reservation (On Payment Success)
- **Trigger:** Payment successfully processed
- **Operation:** Decrement total stock, remove reservation
- **Result:** Stock is now permanently gone from inventory

#### Release Reservation (On Payment Failure/Cancel)
- **Trigger:** Payment failed after 3 retries OR user cancels order
- **Operation:** Return reserved stock to available
- **Result:** Product available for other customers again

### Overselling Prevention

**Critical Requirement:** System must never oversell inventory under high concurrent load.

#### Test Scenario
- Schedule: 100 concurrent checkout requests for same product
- Inventory: 50 units in stock
- Expected Result:
  - First 50 orders succeed with reserved stock
  - Orders 51-100 fail with "Out of Stock" error
  - No partial reservations
  - Exactly 50 units reserved, 0 available

#### Implementation
- **Mechanism:** Optimistic locking with `@Version` annotation on Product entity
- **Conflict:** If version mismatch detected, transaction fails
- **Recovery:** Service retries immediately (max 3 retries)
- **Advantage:** No lock contention, linear scalability

### Stock Adjustment

#### Admin Updates
- Adjust stock for received goods, shrinkage, damage
- **Input:** Product UUID, adjustment amount (positive/negative)
- **Validation:** Final stock ≥ 0
- **Flyway Migration:** All adjustments logged in schema

---

## Feature 7: Notifications

### Notification Events

#### Order Confirmed
- **Trigger:** Order status changes to PAID
- **Content:** "Your order [Order #] has been confirmed. Estimated delivery: [date]"
- **Recipient:** Customer email

#### Payment Success
- **Trigger:** Payment successfully processed
- **Content:** "Payment of $[amount] received for order [Order #]"
- **Recipient:** Customer email

#### Payment Failed
- **Trigger:** Payment fails after 3 retries
- **Content:** "Payment failed for order [Order #]. Please retry or contact support"
- **Recipient:** Customer email

#### Order Shipped
- **Trigger:** Admin updates order to SHIPPED
- **Content:** "Your order [Order #] has shipped! Tracking: [URL]"
- **Recipient:** Customer email

### Notification Mechanism

- **Transport:** Mock email (logs to console)
- **Interface:** Ready for real SMTP integration
- **Async Delivery:** Via Spring ApplicationEventPublisher
- **Tolerance:** Eventual consistency (1-5 second acceptable delay)
- **Retry:** Best effort, no guaranteed delivery

### Mock Email Implementation

```
[MOCK EMAIL]
To: customer@example.com
Subject: Order ORD-2026-001234 Confirmed
Body: Your order has been confirmed...
Sent: 2026-04-30T14:23:45Z
```

---

## Non-Functional Requirements

### Performance

- **Search Response Time:** < 500 ms
- **Order Placement:** < 2 seconds end-to-end
- **API Response:** < 1 second (excluding payment processing)
- **Concurrent Users:** 100+ simultaneous requests supported

### Availability

- **Uptime Target:** 99% during business hours
- **Graceful Degradation:** If email service down, order still succeeds
- **Health Checks:** All services report health status

### Security

- **PCI Compliance:** (Not applicable—mock payment)
- **JWT Verification:** Performed only at the API Gateway (RS256 with RSA public key)
- **Gateway Trust Model:** Downstream services validate `X-Gateway-Secret` instead of verifying JWT directly
- **Header-Based Auth:** Gateway injects `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Gateway-Secret` into every downstream request
- **Direct Access Blocked:** Backend services return 403 without valid `X-Gateway-Secret`
- **Gateway Secret:** Configured via `GATEWAY_INTERNAL_SECRET` environment variable
- **Password Strength:** BCrypt strength 12, minimum 8 characters
- **Input Validation:** All user inputs validated
- **Authorization:** Role-based access enforced at each service (e.g., ADMIN only for product mutations)

---

## Assumptions

1. Users have valid email addresses for notifications
2. Products don't require size/color variants
3. Shipping is flat-rate or free
4. All prices in USD
5. Single currency system
6. One product per cart line (no variants)

---

## Future Enhancements

1. Product reviews and ratings
2. Wishlist functionality
3. Promotional codes and discounts
4. Multi-currency support
5. Product variants (size, color)
6. Digital products (instant download)
7. Subscription orders
8. Gift cards


