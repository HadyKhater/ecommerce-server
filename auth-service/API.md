# Auth Service API Documentation

Base URL: `/api/auth`

---

## POST /api/auth/register

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

## POST /api/auth/login

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

## POST /api/auth/refresh

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

## POST /api/auth/logout

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

## GET /api/auth/me

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

## Error Response Format (RFC 7807)

All errors follow the Problem Details format:

| Field | Description |
|-------|-------------|
| `type` | URI identifying the error type |
| `title` | Short, human-readable summary |
| `detail` | Detailed error description |
| `status` | HTTP status code |
| `timestamp` | ISO 8601 timestamp |

---

## Security Notes

- Tokens are signed using **RS256** (RSA-2048)
- Access tokens expire after 15 minutes (configurable)
- Refresh tokens expire after 7 days (configurable)
- Passwords are hashed using BCrypt with strength 12
- Blacklisted tokens are stored in-memory and rejected on all protected endpoints
