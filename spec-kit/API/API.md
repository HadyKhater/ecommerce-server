# E-Commerce Platform — API Documentation

---

## Gateway Routing Reference

All requests go through the API Gateway on port `8080`.
The gateway applies `StripPrefix=1` — removing `/api` before forwarding to each service.

| External URL (Client) | Internal URL (Service receives) | Service |
|---|---|---|
| `/api/auth/**` | `/auth/**` | auth-service :8081 |
| `/api/products/**` | `/products/**` | product-service :8082 |
| `/api/inventory/**` | `/inventory/**` | inventory-service :8083 |
| `/api/cart/**` | `/cart/**` | cart-service :8084 |
| `/api/orders/**` | `/orders/**` | order-service :8085 |
| `/api/payments/**` | `/payments/**` | payment-service :8086 |
| `/api/notifications/**` | `/notifications/**` | notification-service :8087 |

> Clients and the Angular frontend always use the **External URL**.
> Backend controllers and SecurityConfig always use the **Internal URL**.

### Gateway Authentication & Header Forwarding

The API Gateway verifies every request before forwarding it downstream:

1. **JWT Verification:** RS256 tokens verified using the RSA public key
2. **Claim Extraction:** `userId` via `claims.getSubject()`, `email`, and `roles` extracted from JWT claims
3. **Header Injection:** The following headers are added to every downstream request:
   - `X-User-Id` — user ID from JWT subject
   - `X-User-Email` — user email from JWT claims
   - `X-User-Roles` — user roles from JWT claims
   - `X-Gateway-Secret` — internal secret proving the request came through the gateway
4. **401 Response:** Returned if token is missing or invalid
5. **Public Routes** (no token required):
   - `POST /api/auth/login`
   - `POST /api/auth/register`
   - `POST /api/auth/refresh`

> **Security Model:** All requests must go through the Gateway on port 8080.
> Direct access to backend service ports (e.g., 8082) is blocked — returns 403 without a valid `X-Gateway-Secret`.
> The gateway secret is configured via the `GATEWAY_INTERNAL_SECRET` environment variable.

---

## Auth Service (port 8081)

> **Gateway Note:** The API Gateway uses `StripPrefix=1`.
> External requests use `/api/auth/**` but the Auth Service receives `/auth/**` internally.
> All controller mappings and security rules inside auth-service use `/auth` (without `/api`).

Base URL (external): `/api/auth`
Base URL (internal, after Gateway StripPrefix=1): `/auth`

### POST /api/auth/register

**Description:** Register a new user account.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Password123",
  "fullName": "John Doe"
}
```

**Validation Rules:**
- `email`: Required, valid email format
- `password`: Required, minimum 8 characters, at least one uppercase letter, one lowercase letter, and one digit
- `fullName`: Required, 2-255 characters

**Response 201:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "fullName": "John Doe",
  "roles": ["USER"],
  "createdAt": "2024-01-01T10:00:00Z"
}
```

**Response 400:**
```json
{
  "type": "https://api.ecommerce.com/errors/validation-error",
  "title": "Validation Failed",
  "errors": {
    "email": "Email is required",
    "password": "Password must be at least 8 characters"
  },
  "timestamp": "2024-01-01T10:00:00Z"
}
```

---

### POST /api/auth/login

**Description:** Authenticate user and receive access/refresh tokens.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "Password123"
}
```

**Validation Rules:**
- `email`: Required, valid email format
- `password`: Required

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Response 401:**
```json
{
  "type": "https://api.ecommerce.com/errors/authentication-failed",
  "title": "Authentication Failed",
  "detail": "Invalid credentials",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

---

### POST /api/auth/refresh

**Description:** Refresh access token using a valid refresh token.

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Validation Rules:**
- `refreshToken`: Required

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

**Response 401:**
```json
{
  "type": "https://api.ecommerce.com/errors/authentication-failed",
  "title": "Authentication Failed",
  "detail": "Invalid or expired refresh token",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

---

### POST /api/auth/logout

**Description:** Logout user and revoke (blacklist) the current access token.

**Headers:**
- `Authorization`: `Bearer <access_token>`

**Request Body:** None

**Response 200:**
```json
{}
```

**Notes:**
- The token is added to an in-memory blacklist
- Blacklisted tokens will be rejected on subsequent requests
- Returns 200 OK even if no valid Authorization header is provided

---

### GET /api/auth/me

**Description:** Get current authenticated user's profile.

**Headers:**
- `X-User-Email`: User's email address (used for identification)

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "fullName": "John Doe",
  "roles": ["USER"],
  "createdAt": "2024-01-01T10:00:00Z"
}
```

**Response 400:**
```json
{
  "type": "https://api.ecommerce.com/errors/invalid-request",
  "title": "Invalid Request",
  "detail": "User not found",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

---

### Error Response Format (RFC 7807)

All errors follow the Problem Details format:

| Field | Description |
|-------|-------------|
| `type` | URI identifying the error type |
| `title` | Short, human-readable summary |
| `detail` | Detailed error description |
| `status` | HTTP status code |
| `timestamp` | ISO 8601 timestamp |

---

### Security Notes

- Tokens are signed using **RS256** (RSA-2048)
- Access tokens expire after 15 minutes (configurable)
- Refresh tokens expire after 7 days (configurable)
- Passwords are hashed using BCrypt with strength 12
- Blacklisted tokens are stored in-memory and rejected on all protected endpoints
- **Gateway-only JWT verification:** Only the API Gateway verifies JWT signatures; downstream services trust the gateway via `X-Gateway-Secret`
- **Header-based auth:** Gateway injects `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Gateway-Secret` into every downstream request

---

### POST /api/auth/logout

**Description:** Logout user and blacklist the current token.

**Headers:**
- `Authorization`: `Bearer <access_token>`

**Request Body:** None

**Response 200:**
```json
{}
```

**Notes:**
- Token is added to in-memory blacklist
- Blacklisted tokens will be rejected on subsequent requests
- Returns 200 even if no valid Authorization header

---

### GET /api/auth/public-key

**Description:** Get RSA public key for JWT signature verification.

**Access:** Public (no authentication required)

**Response 200:**
```json
{
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

**Notes:**
- Used by API Gateway to verify JWT tokens
- Gateway fetches this on startup
- Key is Base64-encoded RSA public key

---

## Product Service (port 8082)

> **Gateway Note:** The API Gateway uses `StripPrefix=1`.
> External requests use `/api/products/**` but the Product Service receives `/products/**` internally.
> All controller mappings and security rules inside product-service use `/products` (without `/api`).

Base URL (external): `/api/products`
Base URL (internal, after Gateway StripPrefix=1): `/products`

### Security Model

Product-Service does **not** verify JWT tokens itself. It trusts the API Gateway:

- **X-Gateway-Secret validation:** Returns `403 Forbidden` if missing or incorrect
- **SecurityContext built from headers:** `X-User-Id` and `X-User-Roles` are used to build the Spring Security context
- **No RS256/JWT logic** in product-service — all JWT verification is done at the gateway

### Authorization Rules

| Method | Endpoint | Required Role |
|--------|----------|---------------|
| GET | `/products` | USER or ADMIN |
| GET | `/products/{id}` | USER or ADMIN |
| POST | `/products` | ADMIN only |
| PUT | `/products/{id}` | ADMIN only |
| DELETE | `/products/{id}` | ADMIN only |

### GET /api/products

**Description:** List products with search/filter and pagination.

**Headers:**
- `X-User-Id`: User ID (injected by gateway)
- `X-User-Roles`: User roles (injected by gateway)
- `X-Gateway-Secret`: Gateway internal secret

**Query Parameters:**
- `keyword` (optional): Search term for name/description
- `category` (optional): Filter by category
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `page` (optional, default 0): Page number
- `size` (optional, default 25): Items per page

**Response 200:**
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Wireless Headphones",
      "description": "Premium noise-cancelling headphones",
      "price": 199.99,
      "category": "Electronics",
      "stock": 50,
      "imageUrl": "https://example.com/headphones.jpg",
      "createdAt": "2026-01-01T10:00:00Z",
      "updatedAt": "2026-01-01T10:00:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 4,
  "number": 0,
  "size": 25
}
```

---

### GET /api/products/{id}

**Description:** Get a single product by ID.

**Headers:**
- `X-User-Id`: User ID (injected by gateway)
- `X-User-Roles`: User roles (injected by gateway)
- `X-Gateway-Secret`: Gateway internal secret

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Wireless Headphones",
  "description": "Premium noise-cancelling headphones",
  "price": 199.99,
  "category": "Electronics",
  "stock": 50,
  "imageUrl": "https://example.com/headphones.jpg",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

**Response 404:**
```json
{
  "type": "https://api.ecommerce.com/errors/product-not-found",
  "title": "Product Not Found",
  "detail": "Product with ID 550e8400-... was not found",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### POST /api/products

**Description:** Create a new product. **ADMIN only.**

**Headers:**
- `X-User-Id`: User ID (injected by gateway)
- `X-User-Roles`: Must contain `ADMIN` (enforced by gateway)
- `X-Gateway-Secret`: Gateway internal secret

**Request Body:**
```json
{
  "name": "Wireless Headphones",
  "description": "Premium noise-cancelling headphones",
  "price": 199.99,
  "categoryId": "550e8400-e29b-41d4-a716-446655440001",
  "stock": 50,
  "imageUrl": "https://example.com/headphones.jpg"
}
```

**Response 201:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Wireless Headphones",
  "description": "Premium noise-cancelling headphones",
  "price": 199.99,
  "category": "Electronics",
  "stock": 50,
  "imageUrl": "https://example.com/headphones.jpg",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

**Response 403:**
```json
{
  "type": "https://api.ecommerce.com/errors/forbidden",
  "title": "Forbidden",
  "detail": "Admin role required",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

### PUT /api/products/{id}

**Description:** Update an existing product. **ADMIN only.**

**Headers:**
- `X-User-Id`: User ID (injected by gateway)
- `X-User-Roles`: Must contain `ADMIN`
- `X-Gateway-Secret`: Gateway internal secret

**Request Body:** Same fields as POST, all optional.

**Response 200:** Updated product.

---

### DELETE /api/products/{id}

**Description:** Delete a product. **ADMIN only.**

**Headers:**
- `X-User-Id`: User ID (injected by gateway)
- `X-User-Roles`: Must contain `ADMIN`
- `X-Gateway-Secret`: Gateway internal secret

**Response 204:** No content.

---

---

## Inventory Service (port 8083)

Base URL (external): `/api/inventory`
Base URL (internal, after Gateway StripPrefix=1): `/inventory`

### Security Model

- All endpoints require `X-Gateway-Secret` header
- Admin endpoints require `ADMIN` role in `X-User-Roles`

### Endpoints

#### GET /api/inventory

**Description:** Get all inventory items.

**Access:** USER, ADMIN

**Response 200:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
    "quantityAvailable": 100,
    "quantityReserved": 0,
    "lowStockThreshold": 10,
    "isLowStock": false,
    "isOutOfStock": false,
    "createdAt": "2026-01-01T10:00:00Z",
    "updatedAt": "2026-01-01T10:00:00Z"
  }
]
```

---

#### GET /api/inventory/{productId}

**Description:** Get inventory for a specific product.

**Access:** USER, ADMIN

**Response 200:** InventoryResponse

**Response 404:** Product not found in inventory

---

#### GET /api/inventory/{productId}/check?quantity={n}

**Description:** Check if stock is available for a product.

**Access:** USER, ADMIN

**Query Parameters:**
- `quantity` (required): Quantity to check

**Response 200:**
```json
{
  "available": true,
  "quantityAvailable": 100
}
```

---

#### GET /api/inventory/low-stock

**Description:** Get items with low stock (quantityAvailable <= lowStockThreshold).

**Access:** ADMIN only

**Response 200:** Array of InventoryResponse

---

#### GET /api/inventory/out-of-stock/count

**Description:** Get count of out-of-stock items.

**Access:** USER, ADMIN

**Response 200:**
```json
{
  "count": 3
}
```

---

#### GET /api/inventory/{productId}/history

**Description:** Get stock movement history for a product.

**Access:** ADMIN only

**Response 200:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
    "movementType": "RESTOCK",
    "quantity": 50,
    "reason": "Initial stock",
    "createdAt": "2026-01-01T10:00:00Z"
  }
]
```

---

#### POST /api/inventory/restock

**Description:** Restock a product (increase available quantity).

**Access:** ADMIN only

**Request Body:**
```json
{
  "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
  "quantity": 50,
  "reason": "New shipment received"
}
```

**Response 200:** Updated InventoryResponse

---

#### PUT /api/inventory/{productId}/adjust

**Description:** Adjust stock levels directly (admin override).

**Access:** ADMIN only

**Request Body:**
```json
{
  "quantityAvailable": 100,
  "quantityReserved": 5,
  "lowStockThreshold": 10,
  "reason": "Stock count correction"
}
```

**Response 200:** Updated InventoryResponse

---

#### POST /api/inventory/reserve

**Description:** Reserve stock for an order.

**Access:** USER, ADMIN (called by Order Service during checkout)

**Request Body:**
```json
{
  "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
  "quantity": 2,
  "reason": "Order ORD-2026-001234"
}
```

**Response 200:** Updated InventoryResponse

**Response 400:** Insufficient stock

---

#### POST /api/inventory/release

**Description:** Release reserved stock (on payment failure or order cancellation).

**Access:** USER, ADMIN

**Request Body:**
```json
{
  "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
  "quantity": 2,
  "reason": "Payment failed for order ORD-2026-001234"
}
```

**Response 200:** Updated InventoryResponse

---

#### POST /api/inventory/initialize/{productId}

**Description:** Initialize inventory for a product that doesn't have inventory yet.

**Access:** ADMIN only

**Request Body:**
```json
{
  "initialQuantity": 100
}
```

**Response 201:** Created InventoryResponse

---

## Cart Service (port 8084)

Base URL (external): `/api/cart`
Base URL (internal, after Gateway StripPrefix=1): `/cart`

### Security Model

- All endpoints require `X-Gateway-Secret` header
- User identification via `X-User-Email` header (not JWT verification - gateway handles that)

### Cart Response Format

```json
{
  "cartId": "550e8400-e29b-41d4-a716-446655440000",
  "userEmail": "user@example.com",
  "items": [
    {
      "cartItemId": "550e8400-e29b-41d4-a716-446655440001",
      "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
      "productName": "Wireless Headphones",
      "quantity": 2,
      "unitPrice": 199.99,
      "totalPrice": 399.98,
      "imageUrl": "https://example.com/headphones.jpg",
      "createdAt": "2026-01-01T10:00:00Z",
      "updatedAt": "2026-01-01T10:00:00Z"
    }
  ],
  "totalItemsCount": 2,
  "grandTotal": 399.99,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

### Endpoints

#### GET /api/cart

**Description:** Get current user's cart with all items.

**Access:** USER, ADMIN (identified by X-User-Email header)

**Response 200:** CartResponse

**Response 404:** Cart not found (user hasn't created one yet)

---

#### POST /api/cart/items

**Description:** Add an item to cart. Creates cart if doesn't exist. Increments quantity if product already in cart.

**Access:** USER, ADMIN

**Request Body:**
```json
{
  "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
  "quantity": 2
}
```

**Response 200:** Updated CartResponse

**Response 400:** Invalid request (product not found, insufficient stock)

---

#### PUT /api/cart/items/{productId}

**Description:** Update quantity of an item in cart. Quantity 0 removes the item.

**Access:** USER, ADMIN

**Request Body:**
```json
{
  "quantity": 3
}
```

**Response 200:** Updated CartResponse

**Response 404:** Item not in cart

---

#### DELETE /api/cart/items/{productId}

**Description:** Remove an item from cart.

**Access:** USER, ADMIN

**Response 200:** Updated CartResponse

**Response 404:** Item not in cart

---

#### DELETE /api/cart/clear

**Description:** Clear all items from cart (empty cart).

**Access:** USER, ADMIN

**Response 200:** Empty CartResponse (or 204 No Content)

---

## Order Service (port 8085)

> **Gateway Note:** The API Gateway uses `StripPrefix=1`.
> External requests use `/api/orders/**` but the Order Service receives `/orders/**` internally.
> All controller mappings and security rules inside order-service use `/orders` (without `/api`).

Base URL (external): `/api/orders`
Base URL (internal, after Gateway StripPrefix=1): `/orders`

### Security Model

- All endpoints require `X-Gateway-Secret` header
- User identification via `X-User-Id` header (injected by gateway after JWT verification)

### Order Lifecycle & Two-Phase Commit

Orders follow a two-phase commit pattern to ensure orders are only finalized after successful payment:

```
1. POST /orders/reserve     → Creates order with RESERVED status (stock reserved)
2. Payment Processing       → Payment is processed
3a. PUT /orders/{id}/confirm → On payment success → Order becomes CONFIRMED
3b. PUT /orders/{id}/cancel-reservation → On payment failure → Order becomes CANCELLED, stock released
```

**Order Status Flow:**
```
RESERVED → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
    ↓
CANCELLED (if payment fails or user cancels)
```

### Endpoints

#### POST /api/orders/reserve (Two-Phase Commit - Phase 1)

**Description:** Create a temporary order reservation awaiting payment. Stock is reserved but the order is not finalized.

**Access:** USER, ADMIN

**Request Body:**
```json
{
  "items": [
    {
      "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
      "quantity": 2
    }
  ],
  "shippingAddress": "123 Main St, Cairo, Egypt",
  "currency": "EGP",
  "userEmail": "user@example.com",
  "promoCode": "SAVE10",
  "discountAmount": 100.00
}
```

**Server-Side Discount Validation:**
- The promo code is re-validated server-side against the product-service
- Discount amount is recalculated on the server to prevent client manipulation
- If validation fails, the order proceeds without discount

**Response 201:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-uuid",
  "userEmail": "user@example.com",
  "status": "RESERVED",
  "totalAmount": 299.98,
  "currency": "EGP",
  "shippingAddress": "123 Main St, Cairo, Egypt",
  "items": [
    {
      "id": "item-uuid",
      "productId": "d1fe25ab-b259-4c8e-ab13-babb47d5fade",
      "productName": "Wireless Headphones",
      "quantity": 2,
      "unitPrice": 199.99,
      "totalPrice": 399.98
    }
  ],
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

---

#### PUT /api/orders/{orderId}/confirm (Two-Phase Commit - Phase 2a)

**Description:** Confirm a reserved order after successful payment. Converts status from RESERVED to CONFIRMED.

**Access:** USER, ADMIN

**Response 200:** Updated OrderResponse with status CONFIRMED

**Response 400:** Order is not in RESERVED status

---

#### PUT /api/orders/{orderId}/cancel-reservation (Two-Phase Commit - Phase 2b)

**Description:** Cancel a reserved order (e.g., on payment failure). Releases reserved stock and sets status to CANCELLED.

**Access:** USER, ADMIN

**Response 200:** Updated OrderResponse with status CANCELLED

**Response 400:** Order is not in RESERVED status

---

#### POST /api/orders (Legacy - Direct Order Creation)

**Description:** Create an order directly (bypasses two-phase commit). Use for backward compatibility.

**Access:** USER, ADMIN

**Note:** This endpoint creates an order with PENDING status. For new integrations, use `/orders/reserve` + confirm flow.

---

#### PUT /api/orders/{orderId}/payment-status

**Description:** Internal endpoint used by Payment Service to update order payment status.

**Access:** Internal (requires X-Gateway-Secret)

**Request Parameters:**
- `status`: COMPLETED or FAILED

**Behavior:**
- COMPLETED → Sets order status to CONFIRMED
- FAILED → Sets order status to CANCELLED and releases reserved stock

---

#### PUT /api/orders/{orderId}/cancel

**Description:** Cancel an order (for non-reserved orders).

**Access:** USER, ADMIN

---

#### GET /api/orders/{orderId}

**Description:** Get order details by ID.

**Access:** USER, ADMIN (users can only view their own orders)

---

#### GET /api/orders/user/{userId}

**Description:** Get all orders for a specific user.

**Access:** USER, ADMIN (users can only view their own orders)

---

#### GET /api/orders/admin/all

**Description:** Get all orders (admin only).

**Access:** ADMIN only

---

#### PUT /api/orders/admin/{orderId}/status

**Description:** Update order status (admin only).

**Access:** ADMIN only

---

## Payment Service (port 8086)

> **Gateway Note:** The API Gateway uses `StripPrefix=1`.
> External requests use `/api/payments/**` but the Payment Service receives `/payments/**` internally.

Base URL (external): `/api/payments`
Base URL (internal, after Gateway StripPrefix=1): `/payments`

### Payment Flow

The Payment Service implements a two-phase commit pattern:

1. **Order Reservation:** Order is created with RESERVED status (stock held)
2. **Payment Processing:** Payment is processed via chosen method
3. **On Success:** Order is confirmed (RESERVED → CONFIRMED)
4. **On Failure:** Reservation is cancelled (RESERVED → CANCELLED), stock released

### Endpoints

#### POST /api/payments/process

**Description:** Process a payment for an order.

**Access:** USER, ADMIN

**Request Body:**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 299.98,
  "currency": "EGP",
  "paymentMethod": "CREDIT_CARD"
}
```

**Response 200:**
```json
{
  "id": "payment-uuid",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-uuid",
  "amount": 299.98,
  "currency": "EGP",
  "status": "SUCCESS",
  "paymentMethod": "CREDIT_CARD",
  "transactionId": "TXN-12345678",
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:05Z"
}
```

---

### Stripe Integration

#### POST /api/stripe/checkout

**Description:** Create a Stripe checkout session.

**Request Body:**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 600,
  "currency": "usd",
  "items": [
    {
      "name": "Wireless Headphones",
      "amount": 400,
      "currency": "usd",
      "quantity": 1
    }
  ],
  "discountCode": "SAVE10",
  "discountType": "PERCENTAGE",
  "discountValue": 10
}
```

**Discount Handling:**
- Discounts are applied via Stripe's built-in coupon system
- The backend creates a Stripe coupon with the discount code
- Coupon is applied to the checkout session
- Discount info is stored in session metadata for tracking

**Response 200:**
```json
{
  "sessionId": "cs_test_...",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "url": "https://checkout.stripe.com/..."
}
```

---

#### POST /api/stripe/webhook

**Description:** Stripe webhook endpoint for payment events.

**Events Handled:**
- `checkout.session.completed` → Confirms the reserved order
- `payment_intent.succeeded` → Logs successful payment
- `payment_intent.payment_failed` → Logs failed payment

---

### Discount & Coupon Flow

1. **Validation:** Promo code is validated against Product Service
2. **Calculation:** Discount amount is calculated server-side
3. **Application:** 
   - For direct payments: Discount is subtracted from order total
   - For Stripe: Discount is applied via Stripe coupon system
4. **Tracking:** Promotion usage is recorded after successful payment

---

## Notification Service (port 8087)

[placeholder — to be filled]
