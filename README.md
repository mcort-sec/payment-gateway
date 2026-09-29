# Payment Gateway API

A robust, enterprise-grade Payment Gateway backend built with **Java 17** and **Spring Boot 4.1.1**, adhering strictly to **Clean Architecture / Hexagonal Architecture** principles.

The gateway provides payment processing and refund workflows with multi-tenant isolation, cryptographically secure API key authentication (Merchant and Processor scopes), strict idempotency guarantees, optimistic and pessimistic concurrency controls, and structured JSON error responses.

---

## Table of Contents

- [Architecture & Design Principles](#architecture--design-principles)
- [Technology Stack](#technology-stack)
- [Prerequisites & Local Environment Setup](#prerequisites--local-environment-setup)
- [Database Migrations (Flyway)](#database-migrations-flyway)
- [Authentication & API Key Scheme](#authentication--api-key-scheme)
- [REST API Reference](#rest-api-reference)
  - [Endpoints Summary](#endpoints-summary)
  - [Merchant Endpoints](#merchant-endpoints)
  - [Processor Endpoints](#processor-endpoints)
- [Idempotency & Replay Mechanism](#idempotency--replay-mechanism)
- [Refund Accounting & Capacity Model](#refund-accounting--capacity-model)
- [Concurrency & Locking Strategy](#concurrency--locking-strategy)
- [Multi-Tenant Security & Isolation](#multi-tenant-security--isolation)
- [Error Handling Contract](#error-handling-contract)
- [Testing & Quality Assurance](#testing--quality-assurance)
- [Key Architectural Decisions](#key-architectural-decisions)
- [Operational & Security Notes](#operational--security-notes)

---

## Architecture & Design Principles

The application is structured into four distinct layers following **Clean Architecture (Hexagonal / Ports and Adapters)**:

```
src/main/java/com/miguelcortes/paymentgateway/
├── domain/                  # Enterprise business rules (Pure Java POJOs)
│   ├── model/               # Aggregates & Value Objects (Payment, Refund, Merchant, Processor, Currency, Statuses)
│   ├── event/               # Domain events
│   └── exception/           # Pure domain exceptions (InvalidPaymentStateException, etc.)
├── application/             # Application business rules (Use Cases & Ports)
│   ├── command/             # Inbound command objects (CreatePaymentCommand, CreateRefundCommand)
│   ├── port/
│   │   └── out/             # Outbound ports / repository interfaces (PaymentRepositoryPort, RefundRepositoryPort, etc.)
│   ├── usecase/             # Use Case Interactors (Pure Java POJOs)
│   └── exception/           # Application exceptions (PaymentNotFoundException, IdempotencyConflictException, etc.)
├── infrastructure/          # Frameworks, drivers & external adapters
│   ├── persistence/         # Spring Data JPA entities, repositories, and persistence adapters
│   ├── security/            # Spring Security filters, principal resolvers, authentication handlers
│   ├── config/              # Spring configuration beans (ApplicationConfig wires POJO use cases)
│   └── crypto/              # Cryptographic utilities (SHA-256 API key hasher, key generators)
└── entrypoint/              # Web presentation layer
    └── rest/                # Spring @RestController, Request/Response DTOs, GlobalExceptionHandler
```

### Key Architectural Tenets:
1. **Framework-Agnostic Domain & Application Core**: The `domain` and `application` packages contain zero Spring, JPA, or HTTP dependencies. Domain models and Use Cases are pure Java POJOs.
2. **Explicit Dependency Injection**: Use cases are instantiated as Spring beans in `infrastructure.config.ApplicationConfig` rather than using `@Service` or `@Component` directly on use case classes.
3. **Ports & Adapters**: Inbound commands drive use cases, while outbound repository ports decouple the application from Spring Data JPA infrastructure.

---

## Technology Stack

- **Language & Runtime**: Java 17 (LTS)
- **Framework**: Spring Boot 4.1.1 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`)
- **Database**: PostgreSQL 15 (`postgres:15-alpine`)
- **Schema Management**: Flyway Community Edition
- **Testing**:
  - JUnit 5 (`junit-jupiter`)
  - AssertJ & Mockito
  - Testcontainers 2.0.5 (`testcontainers:postgresql`)
  - Spring MockMvc (E2E full-slice web tests)
- **Build Tool**: Apache Maven (via `./mvnw` wrapper)

---

## Prerequisites & Local Environment Setup

### 1. Requirements
- Docker Engine & Docker Compose
- Java Development Kit (JDK) 17+ (optional if using local Maven wrapper with system JDK)

### 2. Configure Environment Variables
Copy the example `.env.example` file to create your local `.env`:

```bash
cp .env.example .env
```

Default local values in `.env.example`:
```dotenv
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=payment_gateway_db
DB_USERNAME=payment_gateway_user
DB_PASSWORD=payment_gateway_secret
```

### 3. Start PostgreSQL with Docker Compose
Run the bundled `compose.yaml` to spin up the local PostgreSQL container:

```bash
docker compose up -d
```

### 4. Run the Application
Export the environment variables and boot the application via Maven:

```bash
set -a && source .env && set +a
./mvnw spring-boot:run
```

The application starts on port `8080` by default. Flyway automatically applies all pending migrations on startup.

---

## Database Migrations (Flyway)

Database evolution is managed sequentially via Flyway in `src/main/resources/db/migration`:

| Migration | File | Description | Key Constraints & Indexes |
|---|---|---|---|
| **V1** | `V1__create_payments_table.sql` | Initial `payments` schema | `chk_payments_amount` (`amount > 0`), `uq_payments_customer_idempotency` |
| **V2** | `V2__add_version_to_payments.sql` | Adds optimistic locking column | `version BIGINT NOT NULL DEFAULT 0` |
| **V3** | `V3__rename_customer_to_merchant.sql` | Domain terminology refinement | Renames `customer_id` $\to$ `merchant_id`, updates unique index to `uq_payments_merchant_idempotency` |
| **V4** | `V4__create_merchants_and_add_payment_fk.sql` | Merchant entity & relational integrity | Creates `merchants` table, adds foreign key `fk_payments_merchants` |
| **V5** | `V5__create_api_credentials.sql` | Merchant authentication | Creates `api_credentials` table, unique `uq_api_credentials_key_prefix`, index `idx_api_credentials_merchant_id` |
| **V6** | `V6__create_processors_and_credentials.sql` | Processor entity & credentials | Creates `processors` & `processor_credentials` tables, FK `fk_processor_credentials_processors`, unique `uq_processor_credentials_key_prefix` |
| **V7** | `V7__create_refunds.sql` | Refund aggregate persistence | Creates `refunds` table, `version` column, FKs to `payments` and `merchants`, index `idx_refunds_payment_id`, unique constraint `uq_refunds_merchant_idempotency` |

---

## Authentication & API Key Scheme

The API secures endpoints using role-based Bearer tokens passed via the `Authorization` header:

```http
Authorization: Bearer <API_KEY>
```

### Key Formats

| Role | Key Format Pattern | Example |
|---|---|---|
| **Merchant** | `pg_test_<12_base62_chars>_<43_base64url_chars>` | `pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8` |
| **Processor** | `pg_proc_test_<12_base62_chars>_<43_base64url_chars>` | `pg_proc_test_pQ1rS2tU3vW4_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2` |

### Security Mechanism
1. **Indexed Prefix Lookup & Hash Comparison**: The key prefix is used for an indexed credential lookup, after which the supplied secret is verified against the stored SHA-256 hash (`key_hash`).
2. **Credential Secret Protection**: Plaintext API keys are never stored in the database.
3. **Revocation & Status Checks**:
   - Inactive or revoked credentials return **401 Unauthorized**.
   - Suspended merchants or processors return **403 Forbidden**.
   - Attempting to access an endpoint with the wrong principal role (e.g., Merchant calling a Processor endpoint) returns **403 Forbidden**.

---

## REST API Reference

### Endpoints Summary

| Method | Path | Principal Role | Idempotency | Success Status | Description |
|---|---|---|---|---|---|
| `POST` | `/payments` | `Merchant` | **Required** | `201 Created` | Authorize / initiate a new payment |
| `GET` | `/payments` | `Merchant` | N/A | `200 OK` | List payment transaction history (Paginated, Tenant-scoped) |
| `GET` | `/payments/{id}` | `Merchant` | N/A | `200 OK` | Fetch payment details by ID (Tenant-scoped) |
| `POST` | `/payments/{id}/cancel` | `Merchant` | N/A | `200 OK` | Cancel a pending payment |
| `POST` | `/payments/{paymentId}/refunds` | `Merchant` | **Required** | `201 Created` | Request a refund on an approved payment |
| `GET` | `/refunds` | `Merchant` | N/A | `200 OK` | List refund transaction history (Paginated, Tenant-scoped) |
| `GET` | `/refunds/{id}` | `Merchant` | N/A | `200 OK` | Fetch refund details by ID (Tenant-scoped) |
| `POST` | `/payments/{id}/approve` | `Processor` | N/A | `200 OK` | Processor callback to approve a payment |
| `POST` | `/payments/{id}/decline` | `Processor` | N/A | `200 OK` | Processor callback to decline a payment |
| `POST` | `/refunds/{id}/approve` | `Processor` | N/A | `200 OK` | Processor callback to approve a pending refund |
| `POST` | `/refunds/{id}/decline` | `Processor` | N/A | `200 OK` | Processor callback to decline a pending refund |

---

### Merchant Endpoints

#### 1. Create Payment
Initiates a new payment transaction. Requires an `Idempotency-Key` header.

- **URL**: `POST /payments`
- **Headers**:
  - `Authorization: Bearer pg_test_...`
  - `Idempotency-Key: <unique-client-key>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "amount": 5000,
    "currency": "USD"
  }
  ```
  *(Note: Amounts are represented as integer `long` values. The API does not use floating-point monetary values, and clients must use the integer-unit convention expected by the API).*

- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/payments \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8" \
    -H "Idempotency-Key: pay-req-001" \
    -H "Content-Type: application/json" \
    -d '{"amount": 5000, "currency": "USD"}'
  ```

- **Response (`201 Created`)**:
  ```http
  HTTP/1.1 201 Created
  Location: http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6
  Content-Type: application/json

  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 5000,
    "currency": "USD",
    "status": "PENDING",
    "createdAt": "2026-09-29T18:00:00Z"
  }
  ```

---

#### 2. List Payments
Lists paginated payment history for the authenticated merchant.

- **URL**: `GET /payments`
- **Query Parameters**:
  - `page` *(optional integer, default: `0`, min: `0`)*: Zero-based page index.
  - `size` *(optional integer, default: `20`, min: `1`, max: `100`)*: Number of records per page.
- **Tenant Scoping**: Automatically scoped to the authenticated merchant identified by the API key (the `merchantId` parameter is not accepted).
- **Merchant Status**: Permitted for active and suspended merchants.
- **Ordering**: Deterministic ordering by `createdAt DESC` with `id DESC` as tie-breaker.
- **Offset Pagination Semantics**: Uses page/size offset pagination. Under concurrent write traffic, offset pagination does not represent a static global snapshot.
- **cURL Example**:
  ```bash
  curl -i -X GET "http://localhost:8080/payments?page=0&size=20" \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8"
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        "amount": 5000,
        "currency": "USD",
        "status": "APPROVED",
        "createdAt": "2026-09-29T18:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

---

#### 3. Get Payment
Retrieves payment status by ID. Returns `404 Not Found` if the payment does not exist or belongs to another merchant.

- **URL**: `GET /payments/{id}`
- **cURL Example**:
  ```bash
  curl -i -X GET http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6 \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8"
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 5000,
    "currency": "USD",
    "status": "APPROVED",
    "createdAt": "2026-09-29T18:00:00Z"
  }
  ```

---

#### 4. Cancel Payment
Cancels a payment that is currently in `PENDING` status.

- **URL**: `POST /payments/{id}/cancel`
- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/cancel \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8"
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 5000,
    "currency": "USD",
    "status": "CANCELLED",
    "createdAt": "2026-09-29T18:00:00Z"
  }
  ```

---

#### 5. Create Refund
Requests a refund on an `APPROVED` payment. Requires `Idempotency-Key`.

- **URL**: `POST /payments/{paymentId}/refunds`
- **Headers**:
  - `Authorization: Bearer pg_test_...`
  - `Idempotency-Key: <unique-refund-key>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "amount": 2000
  }
  ```

- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/refunds \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8" \
    -H "Idempotency-Key: ref-req-001" \
    -H "Content-Type: application/json" \
    -d '{"amount": 2000}'
  ```

- **Response (`201 Created`)**:
  ```http
  HTTP/1.1 201 Created
  Location: http://localhost:8080/refunds/7c9e6679-7425-40de-944b-e07fc1f90ae7
  Content-Type: application/json

  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "paymentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 2000,
    "currency": "USD",
    "status": "PENDING",
    "createdAt": "2026-09-29T18:05:00Z"
  }
  ```

---

#### 6. List Refunds
Lists paginated refund history for the authenticated merchant.

- **URL**: `GET /refunds`
- **Query Parameters**:
  - `page` *(optional integer, default: `0`, min: `0`)*: Zero-based page index.
  - `size` *(optional integer, default: `20`, min: `1`, max: `100`)*: Number of records per page.
- **Tenant Scoping**: Automatically scoped to the authenticated merchant identified by the API key (the `merchantId` parameter is not accepted).
- **Merchant Status**: Permitted for active and suspended merchants.
- **Ordering**: Deterministic ordering by `createdAt DESC` with `id DESC` as tie-breaker.
- **Offset Pagination Semantics**: Uses page/size offset pagination. Under concurrent write traffic, offset pagination does not represent a static global snapshot.
- **cURL Example**:
  ```bash
  curl -i -X GET "http://localhost:8080/refunds?page=0&size=20" \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8"
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "paymentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        "amount": 2000,
        "currency": "USD",
        "status": "APPROVED",
        "createdAt": "2026-09-29T18:05:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

---

#### 7. Get Refund
Retrieves refund status by ID. Tenant-scoped (returns `404 Not Found` if accessed across merchants).

- **URL**: `GET /refunds/{id}`
- **cURL Example**:
  ```bash
  curl -i -X GET http://localhost:8080/refunds/7c9e6679-7425-40de-944b-e07fc1f90ae7 \
    -H "Authorization: Bearer pg_test_aB3dE5gH7jK9_u8v9w0x1y2z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8"
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "paymentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "amount": 2000,
    "currency": "USD",
    "status": "APPROVED",
    "createdAt": "2026-09-29T18:05:00Z"
  }
  ```

---

### Processor Endpoints

#### 1. Approve Payment
- **URL**: `POST /payments/{id}/approve`
- **Headers**: `Authorization: Bearer pg_proc_test_...`
- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/approve \
    -H "Authorization: Bearer pg_proc_test_pQ1rS2tU3vW4_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2"
  ```
- **Response (`200 OK`)**: Returns `PaymentResponse` with `status: "APPROVED"`.

#### 2. Decline Payment
- **URL**: `POST /payments/{id}/decline`
- **Headers**: `Authorization: Bearer pg_proc_test_...`
- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/decline \
    -H "Authorization: Bearer pg_proc_test_pQ1rS2tU3vW4_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2"
  ```
- **Response (`200 OK`)**: Returns `PaymentResponse` with `status: "DECLINED"`.

#### 3. Approve Refund
- **URL**: `POST /refunds/{id}/approve`
- **Headers**: `Authorization: Bearer pg_proc_test_...`
- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/refunds/7c9e6679-7425-40de-944b-e07fc1f90ae7/approve \
    -H "Authorization: Bearer pg_proc_test_pQ1rS2tU3vW4_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2"
  ```
- **Response (`200 OK`)**: Returns `RefundResponse` with `status: "APPROVED"`.

#### 4. Decline Refund
- **URL**: `POST /refunds/{id}/decline`
- **Headers**: `Authorization: Bearer pg_proc_test_...`
- **cURL Example**:
  ```bash
  curl -i -X POST http://localhost:8080/refunds/7c9e6679-7425-40de-944b-e07fc1f90ae7/decline \
    -H "Authorization: Bearer pg_proc_test_pQ1rS2tU3vW4_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2"
  ```
- **Response (`200 OK`)**: Returns `RefundResponse` with `status: "DECLINED"`.

---

## Idempotency & Replay Mechanism

Both `POST /payments` and `POST /payments/{paymentId}/refunds` require the `Idempotency-Key` header.

### Scope & Constraints
- **Scope**: Idempotency is strictly scoped per `(merchant_id, idempotency_key)` pair.
- **Database Enforcement**:
  - `payments`: Unique constraint `uq_payments_merchant_idempotency (merchant_id, idempotency_key)`
  - `refunds`: Unique constraint `uq_refunds_merchant_idempotency (merchant_id, idempotency_key)`

### Behavior Matrix
1. **Identical Replay**: If a request arrives with an existing `Idempotency-Key` and an **identical payload** (`amount`, `currency`, and `paymentId`), the gateway returns the **original resource** with `201 Created` (safe idempotency replay).
2. **Payload Conflict**: If the `Idempotency-Key` is reused with **different parameters** (e.g., altered amount or currency), the request fails immediately with **409 Conflict** (`IdempotencyConflictException`).

---

## Refund Accounting & Capacity Model

The gateway enforces atomic refund capacity tracking to guarantee that a payment cannot be over-refunded, even under high concurrency.

### Formula
$$\text{Available Capacity} = \text{Payment.amount} - \sum_{\text{refund} \in \{\text{PENDING, APPROVED}\}} \text{refund.amount}$$

### Rules:
1. **Eligible Status**: Only payments in `APPROVED` status can be refunded. Attempting to refund `PENDING`, `DECLINED`, or `CANCELLED` payments returns **409 Conflict** (`InvalidPaymentStateException`).
2. **Atomic Reservation**: When a refund is created in `PENDING` state, its amount is immediately subtracted from available capacity.
3. **Exceeding Capacity**: If $\text{requested\_amount} > \text{Available Capacity}$, the creation fails with **409 Conflict** (`RefundAmountExceedsAvailableException`).
4. **Capacity Release**: If a refund transitions to `DECLINED`, its reserved amount is released and becomes available again for subsequent refund requests.

---

## Concurrency & Locking Strategy

The system uses a hybrid locking model to ensure data consistency and high throughput:

```mermaid
flowchart TD
    A[Concurrent Client Request] --> B{Operation Type}
    B -->|State Transitions: Approve/Decline/Cancel| C[Optimistic Locking: @Version]
    C -->|Version match| D[Commit Transaction]
    C -->|Version mismatch| E[409 Conflict: ConcurrentModificationException]
    B -->|Refund Creation: Reserve Capacity| F[Pessimistic Write Lock: @Lock PESSIMISTIC_WRITE]
    F -->|Row lock on Payment| G[Compute Capacity & Insert Refund]
    G --> H[Release Row Lock & Commit]
```

1. **Optimistic Locking (`@Version`)**:
   - Applied to both `Payment` and `Refund` entities.
   - Guards against race conditions during processor callbacks (`approve`, `decline`) and cancellations.
   - Concurrent updates trigger concurrent modification exceptions mapped to **409 Conflict**.
2. **Pessimistic Write Locking (`PESSIMISTIC_WRITE`)**:
   - Applied to the parent `Payment` row during `CreateRefundUseCase`.
   - Serializes concurrent refund creations against the same payment, preventing race conditions on capacity calculation (TOCTOU: Time-Of-Check to Time-Of-Use).

---

## Multi-Tenant Security & Isolation

- Every merchant is strictly isolated by `merchantId`.
- **Tenant-Safe Lookups**: When querying a payment or refund (`GET /payments/{id}`, `GET /refunds/{id}`), the query filters by both `id` AND `merchantId`.
- **Information Leak Prevention**: If Merchant A queries an ID owned by Merchant B, the API returns **404 Not Found** (identical to non-existent resources), preventing resource enumeration or existence probing.

---

## Error Handling Contract

All error responses across controllers, authentication filters, and access handlers return a consistent JSON payload defined by `ErrorResponse`:

```json
{
  "timestamp": "2026-09-29T18:10:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Refund amount 6000 exceeds available refundable amount 5000",
  "path": "/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/refunds"
}
```

### Fields:
- `timestamp` (*Instant*): ISO-8601 UTC timestamp of when the error occurred.
- `status` (*int*): HTTP status code.
- `error` (*String*): HTTP status reason phrase (e.g. `Bad Request`, `Unauthorized`, `Forbidden`, `Not Found`, `Conflict`).
- `message` (*String*): Detailed human-readable explanation of the error.
- `path` (*String*): Request URI that triggered the error.

### Exception to HTTP Status Mapping

| HTTP Status | Exception Classes / Handlers | Typical Cause |
|---|---|---|
| `400 Bad Request` | `MethodArgumentNotValidException`<br>`HandlerMethodValidationException`<br>`ConstraintViolationException`<br>`MissingRequestHeaderException`<br>`MethodArgumentTypeMismatchException`<br>`HttpMessageNotReadableException`<br>`InvalidPaymentException`<br>`InvalidRefundException`<br>`InvalidProcessorException`<br>`InvalidProcessorCredentialException` | Malformed JSON, negative/zero amount, missing mandatory headers (e.g. `Idempotency-Key`), invalid parameter formats |
| `401 Unauthorized` | `ApiKeyAuthenticationEntryPoint`<br>`AuthenticationException` | Missing or malformed `Authorization` header, unregistered or revoked API key |
| `403 Forbidden` | `ApiAccessDeniedHandler`<br>`AccessDeniedException`<br>`MerchantSuspendedException`<br>`ProcessorSuspendedException` | Insufficient role permissions (e.g. Merchant calling Processor endpoint), suspended merchant or processor account |
| `404 Not Found` | `PaymentNotFoundException`<br>`RefundNotFoundException`<br>`MerchantNotFoundException`<br>`ProcessorNotFoundException` | Resource does not exist, or cross-tenant resource access attempt |
| `409 Conflict` | `IdempotencyConflictException`<br>`RefundAmountExceedsAvailableException`<br>`InvalidPaymentStateException`<br>`InvalidRefundStateException`<br>`PaymentConcurrentModificationException`<br>`RefundConcurrentModificationException` | Reusing idempotency key with different payload, refund exceeding available capacity, invalid state transition, concurrent update conflict |

---

## Testing & Quality Assurance

The codebase features comprehensive test coverage spanning unit, integration, and end-to-end slice tests.

### Running the Test Suite
Execute the entire test suite using the Maven wrapper:

```bash
./mvnw test
```

### Test Taxonomy
- **Domain Unit Tests** (`*Test.java`): Validate domain invariants, entity state machines, and capacity arithmetic with zero mocks and instant execution.
- **Application Use Case Tests**: Test business workflows with Mockito mock ports.
- **Persistence & Repository Tests**: Use real PostgreSQL instances via Testcontainers to verify JPA queries, locking behavior, foreign keys, and unique constraint enforcement.
- **Security Tests**: Validate token parsing, constant-time hashing, role authorization, and suspended entity filters.
- **MockMvc End-to-End Tests** (`*EndToEndTest.java`): Full HTTP-to-Database integration tests validating status codes, `ErrorResponse` payloads, headers, and transactional rollbacks against a real PostgreSQL container.

**Current Test Baseline**: 387 tests, 0 failures, 0 errors, 0 skipped.

---

## Key Architectural Decisions

1. **POJO Use Cases without Framework Annotations**:
   Use cases are isolated from Spring Framework annotations (`@Service`, `@Autowired`). This keeps business logic pure, easily testable in millisecond unit tests, and decoupled from framework lifecycle.
2. **Hashed API Key Authentication**:
   API keys follow a structured `prefix_random` pattern. Lookups use the plaintext prefix, while authentication matches the SHA-256 hash. In the event of a database leak, plaintext secrets remain uncompromised.
3. **Monetary Representation via `long`**:
   Amounts are represented as integer `long` values to avoid IEEE 754 floating-point rounding errors. The API does not use floating-point monetary values, and clients must use the integer-unit convention expected by the API.
4. **Pessimistic Lock on Capacity Reservation vs. Optimistic on Status**:
   Creating a refund performs capacity checks across multiple rows (`SELECT ... FOR UPDATE` on `Payment`), ensuring zero race conditions for available balances. State transitions on individual entities use lightweight `@Version` optimistic concurrency.

---

## Operational & Security Notes

- **Secrets Management**: Never commit `.env` or production API keys to source control. `.env` is listed in `.gitignore`.
- **Domain State Invariants**: Entity lifecycle states (`PaymentStatus`, `RefundStatus`) and transition rules are strictly protected by domain state machine methods.
- **Credential Safety in Storage & Transport**: Plaintext API keys are never persisted in the database, and response DTOs never expose credentials or internal hashes. Future logging must never output raw `Authorization` headers or plaintext keys.
