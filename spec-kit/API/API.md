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

[placeholder — to be filled]

---

## Order Service (port 8085)

[placeholder — to be filled]

---

## Payment Service (port 8086)

[placeholder — to be filled]

---

## Notification Service (port 8087)

[placeholder — to be filled]
