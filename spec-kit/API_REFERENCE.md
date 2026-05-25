# API Endpoint Reference Manual

This document details all exposed REST and WebSocket endpoints in the E-Commerce platform. Every entry lists the HTTP method, resource path, required authorization scope, request body parameters, and response structures.

---

## 🔀 API Gateway Routing (`:8080`)

All client requests must go through the API Gateway on port `8080`.
The gateway utilizes `StripPrefix=1`, removing `/api` before routing downstream:

```
Client -> http://localhost:8080/api/auth/login ──► Gateway routes to ──► http://localhost:8081/auth/login
```

* **Public Paths:** Bypasses JWT requirements but appends the gateway internal secret:
  - `POST /api/auth/login`
  - `POST /api/auth/register`
  - `POST /api/auth/refresh`
  - `GET /api/banners/public`
  - `POST /api/stripe/webhook`
* **Protected Paths:** Requires header `Authorization: Bearer <access_token>` containing a valid RS256 token.

---

## 👤 Auth Service (`:8081`)

Base Internal URL: `/auth`

### 1. Register User
* **Endpoint:** `POST /auth/register`
* **Auth:** Public
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123",
    "fullName": "Jane Doe"
  }
  ```
* **Response 201:**
  ```json
  {
    "id": "c30fa596-f368-450a-8bf8-86d7e2311894",
    "email": "user@example.com",
    "fullName": "Jane Doe",
    "roles": ["USER"],
    "createdAt": "2026-05-23T01:45:00Z"
  }
  ```

### 2. Login User
* **Endpoint:** `POST /auth/login`
* **Auth:** Public
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123"
  }
  ```
* **Response 200:**
  ```json
  {
    "accessToken": "eyJhbGciOiJSUzI1Ni...",
    "refreshToken": "eyJhbGciOiJSUzI1Ni...",
    "expiresIn": 900,
    "tokenType": "Bearer"
  }
  ```

### 3. Refresh Token
* **Endpoint:** `POST /auth/refresh`
* **Auth:** Public
* **Request Body:**
  ```json
  {
    "refreshToken": "eyJhbGciOiJSUzI1Ni..."
  }
  ```
* **Response 200:** Same as login response structure.

### 4. Logout User
* **Endpoint:** `POST /auth/logout`
* **Auth:** Required (Access token blacklisted)
* **Headers:** `Authorization: Bearer <token>`
* **Response 200:** Empty body.

### 5. Get Current Profile
* **Endpoint:** `GET /auth/me`
* **Auth:** Required
* **Headers:** Injected by Gateway (`X-User-Email`)
* **Response 200:** User details record.

### 6. Get JWT Public Verification Key
* **Endpoint:** `GET /auth/public-key`
* **Auth:** Public
* **Response 200:**
  ```json
  {
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgK..."
  }
  ```

---

## 🛍️ Product Service (`:8082`)

Base Internal URL: `/products`, `/categories`, `/promotions`, `/banners`

### 📦 Products Catalog Endpoints

#### 1. Search Products
* **Endpoint:** `GET /products`
* **Auth:** Required (`USER` or `ADMIN`)
* **Query Params:** `keyword` (text), `categoryId` (UUID), `minPrice` (decimal), `maxPrice` (decimal), `page` (int), `size` (int).
* **Response 200:**
  ```json
  {
    "content": [
      {
        "id": "e93ab63c-39c2-4886-905e-f002c9183422",
        "name": "Mechanical Keyboard",
        "description": "RGB backlight mechanical keyboard",
        "price": 89.99,
        "categoryId": "c044b7d1-e630-4be6-bc6f-a189cfb05844",
        "categoryName": "Electronics",
        "active": true,
        "imageUrl": "https://example.com/keyboard.jpg"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 25
  }
  ```

#### 2. Get Product By ID
* **Endpoint:** `GET /products/{id}`
* **Auth:** Required (`USER` or `ADMIN`)
* **Response 200:** Product details object.

#### 3. Create Product
* **Endpoint:** `POST /products`
* **Auth:** `ADMIN` only
* **Request Body:**
  ```json
  {
    "name": "Gaming Mouse",
    "description": "Ergonomic high-DPI mouse",
    "price": 49.99,
    "categoryId": "c044b7d1-e630-4be6-bc6f-a189cfb05844",
    "imageUrl": "https://example.com/mouse.jpg"
  }
  ```
* **Response 201:** Created Product object.

#### 4. Update Product
* **Endpoint:** `PUT /products/{id}`
* **Auth:** `ADMIN` only
* **Request Body:** Same properties as create.
* **Response 200:** Updated Product object.

#### 5. Delete Product
* **Endpoint:** `DELETE /products/{id}`
* **Auth:** `ADMIN` only
* **Response 204:** Empty body.

---

### 📂 Categories Endpoints

#### 1. Get Categories
* **Endpoint:** `GET /categories`
* **Auth:** Required
* **Response 200:** Array of category listings.

#### 2. Create Category
* **Endpoint:** `POST /categories`
* **Auth:** `ADMIN` only
* **Request Body:**
  ```json
  {
    "name": "Electronics",
    "description": "Phones, laptops, accessories",
    "slug": "electronics"
  }
  ```
* **Response 201:** Created Category object.

---

### 🏷️ Promotions & Banners Endpoints

#### 1. Validate Promo Code
* **Endpoint:** `POST /promotions/validate`
* **Auth:** Required
* **Request Body:**
  ```json
  {
    "code": "SUMMER20",
    "orderAmount": 100.00,
    "userId": "c30fa596-f368-450a-8bf8-86d7e2311894",
    "userEmail": "user@example.com"
  }
  ```
* **Response 200:**
  ```json
  {
    "valid": true,
    "discountAmount": 20.00,
    "message": "Promotion code successfully validated"
  }
  ```

#### 2. Get Homepage Banners
* **Endpoint:** `GET /banners/public`
* **Auth:** Public
* **Response 200:** Array of active promotional banners (carousel ready).

---

## 📦 Inventory Service (`:8083`)

Base Internal URL: `/inventory`

### 1. Check Product Stock
* **Endpoint:** `GET /inventory/{productId}/check`
* **Auth:** Required
* **Query Params:** `quantity` (int)
* **Response 200:**
  ```json
  {
    "available": true,
    "quantityAvailable": 120
  }
  ```

### 2. Reserve Stock (Phase 1 Order Placement)
* **Endpoint:** `POST /inventory/reserve`
* **Auth:** Required (Called by Order Service)
* **Request Body:**
  ```json
  {
    "productId": "e93ab63c-39c2-4886-905e-f002c9183422",
    "quantity": 2
  }
  ```
* **Response 200:** Inventory metrics indicating reservation counts.

### 3. Release Stock
* **Endpoint:** `POST /inventory/release`
* **Auth:** Required
* **Request Body:** Same parameters as reserve.
* **Response 200:** Inventory metrics after releasing reservation.

### 4. Admin Direct Stock Adjustment
* **Endpoint:** `PUT /inventory/{productId}/adjust`
* **Auth:** `ADMIN` only
* **Request Body:**
  ```json
  {
    "quantityAvailable": 150,
    "quantityReserved": 0,
    "lowStockThreshold": 5,
    "reason": "Annual stock audit correction"
  }
  ```
* **Response 200:** Updated Inventory metrics.

---

## 🛒 Cart Service (`:8087`)

Base Internal URL: `/cart`

### 1. Get Cart
* **Endpoint:** `GET /cart`
* **Auth:** Required
* **Response 200:**
  ```json
  {
    "cartId": "a90da292-1262-421b-bf88-9992c9103e91",
    "userEmail": "user@example.com",
    "items": [
      {
        "productId": "e93ab63c-39c2-4886-905e-f002c9183422",
        "productName": "Mechanical Keyboard",
        "quantity": 1,
        "unitPrice": 89.99,
        "totalPrice": 89.99,
        "imageUrl": "https://example.com/keyboard.jpg"
      }
    ],
    "totalItemsCount": 1,
    "grandTotal": 89.99
  }
  ```

### 2. Add Item to Cart
* **Endpoint:** `POST /cart/items`
* **Auth:** Required
* **Request Body:**
  ```json
  {
    "productId": "e93ab63c-39c2-4886-905e-f002c9183422",
    "quantity": 1
  }
  ```
* **Response 201:** Updated Cart object.

### 3. Clear Cart
* **Endpoint:** `DELETE /cart/clear`
* **Auth:** Required
* **Response 204:** Empty body.

---

## 📋 Order Service (`:8084`)

Base Internal URL: `/orders`

### 1. Directly Create Order
* **Endpoint:** `POST /orders`
* **Auth:** Required
* **Request Body:**
  ```json
  {
    "shippingAddress": "123 Main St, New York, NY 10001",
    "items": [
      {
        "productId": "e93ab63c-39c2-4886-905e-f002c9183422",
        "quantity": 1,
        "price": 89.99
      }
    ],
    "discountCode": "SUMMER20"
  }
  ```
* **Response 201:**
  ```json
  {
    "id": "df2ab90c-4933-4f9e-a89e-99f2390a3c94",
    "userId": "c30fa596-f368-450a-8bf8-86d7e2311894",
    "status": "CONFIRMED",
    "totalAmount": 69.99,
    "shippingAddress": "123 Main St, New York, NY 10001"
  }
  ```

### 2. Stage 1: Place Order Reservation (Two-Phase Commit)
* **Endpoint:** `POST /orders/reserve`
* **Auth:** Required
* **Request Body:** Same parameters as Create Order.
* **Response 201:** Order object created with status `RESERVED` (expires in 2 minutes if unconfirmed).

### 3. Stage 2: Confirm Order Placement
* **Endpoint:** `PUT /orders/{orderId}/confirm`
* **Auth:** Required
* **Response 200:** Order details showing status changed to `CONFIRMED`.

---

## 💳 Payment Service (`:8085`)

Base Internal URL: `/payments`, `/stripe`

### 💵 Simulated Offline Payments

#### 1. Process Offline Payment
* **Endpoint:** `POST /payments/process`
* **Auth:** Required
* **Request Body:**
  ```json
  {
    "orderId": "df2ab90c-4933-4f9e-a89e-99f2390a3c94",
    "amount": 69.99
  }
  ```
* **Response 200:**
  ```json
  {
    "transactionId": "TXN-2026-004567",
    "status": "SUCCESS",
    "amount": 69.99
  }
  ```

---

### 💳 Stripe Payments Gateway

#### 1. Create Stripe Checkout Session
* **Endpoint:** `POST /stripe/checkout`
* **Auth:** Required
* **Request Body:**
  ```json
  {
    "orderId": "df2ab90c-4933-4f9e-a89e-99f2390a3c94",
    "amount": 69.99,
    "currency": "usd",
    "items": [
      {
        "name": "Mechanical Keyboard",
        "quantity": 1,
        "amount": 89.99
      }
    ],
    "discountCode": "SUMMER20",
    "discountType": "PERCENTAGE",
    "discountValue": 20
  }
  ```
* **Response 200:**
  ```json
  {
    "sessionId": "cs_test_a19Y00a9j013eJ8H91b8G0...",
    "orderId": "df2ab90c-4933-4f9e-a89e-99f2390a3c94",
    "url": "https://checkout.stripe.com/c/pay/cs_test_..."
  }
  ```

#### 2. Stripe Payment Callback Webhook
* **Endpoint:** `POST /stripe/webhook`
* **Auth:** Public (Calls securely verified via cryptographic Stripe signatures)
* **Headers:** `Stripe-Signature: t=1612345...,v1=e23a9a...`
* **Response 200:** Evaluated Stripe event name (e.g., `checkout.session.completed`).

---

## 🔔 Notification Service (`:8086`)

Base Internal URL: `/notifications`

### 1. Get User Notifications
* **Endpoint:** `GET /notifications/user/{userId}`
* **Auth:** Required
* **Response 200:** Array containing top 20 stored user alerts.

### 2. Mark Notification Read
* **Endpoint:** `PUT /notifications/{id}/read`
* **Auth:** Required
* **Response 200:** Notification object showing `isRead = true`.

### 3. WebSocket Channel Connection
* **Protocol Endpoint:** `ws://localhost:8086/ws` (Or fallback STOMP handshake over SockJS wrapper)
* **Private User Broadcast Channel:** `/topic/notifications/{userId}`
