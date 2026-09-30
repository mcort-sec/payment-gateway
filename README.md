# Payment Gateway API

A robust, enterprise-grade Payment Gateway backend built with **Java 17** and **Spring Boot 4.1.1**, adhering strictly to **Clean Architecture / Hexagonal Architecture** principles.

The gateway provides payment processing and refund workflows with multi-tenant isolation, cryptographically secure API key authentication (Merchant and Processor scopes), strict idempotency guarantees, optimistic and pessimistic concurrency controls, and structured JSON error responses.

---

## Table of Contents

- [5-Minute Demo](#5-minute-demo)
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

## 5-Minute Demo

This demo mode is designed for portfolio evaluation and rapid local testing. It boots the Payment Gateway in a self-contained local environment, automatically initializing test actors and deterministic credentials so you can execute and explore the API immediately.

> [!NOTE]
> The demo profile automatically provisions four distinct testing identities (Active Merchant, Suspended Merchant, Active Processor, Suspended Processor) and their credentials via an idempotent startup bootstrap. It does **not** pre-populate payments or refunds—allowing you to interactively execute the entire transaction lifecycle from Postman.

> [!WARNING]
> **DEMO PROFILE ONLY**: The credentials under the `demo` profile are intentionally deterministic and public for portfolio demonstration. Never use them outside a disposable local development environment. The default application profile does not load demo data.

### What This Demo Demonstrates
- **Clean Architecture & Hexagonal Isolation**: Pure Java domain aggregates and interactors completely decoupled from Spring and JPA.
- **Role-Based API Key Authentication**: Cryptographic SHA-256 hashed keys with indexed prefix lookups.
- **Multi-Tenant Security**: Strict merchant tenant isolation and opaque cross-tenant probing defense.
- **Idempotency Contracts**: Safe replays on identical requests and 409 Conflict detection on payload mutations.
- **Transaction Lifecycle & State Machines**: Deterministic payment and refund status progressions (`PENDING` $\to$ `APPROVED` / `DECLINED` / `CANCELLED`).
- **Concurrency & Capacity Controls**: Optimistic locking (`@Version`) for entity updates and pessimistic row locking (`PESSIMISTIC_WRITE`) for real-time refund balance reservation.
- **Suspension Policies & Error Contracts**: Immediate enforcement of merchant and processor suspensions with RFC-compliant, uniform JSON error structures.

---

### Prerequisites
- **Java 17+** (JDK)
- **Docker & Docker Compose**
- **Git**
- **Postman** (Desktop app or web client)

*(Maven does not need to be installed separately; the project includes the `./mvnw` wrapper).*

---

### Step-by-Step Quickstart

#### 1. Clone the Repository
```bash
git clone <your-repository-url>
cd payment-gateway
```

#### 2. Prepare Environment Variables
Copy `.env.example` to create your local `.env`:
```bash
cp .env.example .env
```

Set the database environment variables in your terminal session (Spring Boot requires these variables in its environment):

- **Linux / macOS / Git Bash**:
  ```bash
  set -a && source .env && set +a
  ```
- **Windows PowerShell**:
  ```powershell
  $env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="payment_gateway_db"; $env:DB_USERNAME="payment_gateway_user"; $env:DB_PASSWORD="payment_gateway_secret"
  ```

#### 3. Start PostgreSQL Container
Launch the PostgreSQL 15 database container using Docker Compose:
```bash
docker compose up -d
```
*(You can verify the database is healthy with `docker compose ps`).*

#### 4. Run the Application in Demo Profile
Start the backend with the `demo` profile active:

- **Linux / macOS**:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
  ```
- **Windows**:
  ```cmd
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo
  ```

The application starts on `http://localhost:8080`. On boot, Flyway runs database migrations and the demo bootstrap prints the active credentials banner to console:

```
================================================================================
PAYMENT GATEWAY — DEMO CREDENTIALS LOADED
================================================================================
[PROFILE: demo] Use these credentials to test the API locally via Postman / cURL.

1. ACTIVE MERCHANT:
   Merchant ID : 00000000-0000-0000-0000-000000000001
   API Key     : pg_test_demoActvMerc_ActiveMerchantDemoSecretKeyForTestingV12345
   Permissions : Create / Get / List Payments, Cancel Payments, Create / Get / List Refunds

2. SUSPENDED MERCHANT:
   Merchant ID : 00000000-0000-0000-0000-000000000002
   API Key     : pg_test_demoSuspMerc_SuspendedMerchantDemoSecretKeyTestingV12345
   Permissions : Get / List resources, Cancel PENDING Payments, Create Refunds; Payment Creation returns 403 Forbidden

3. ACTIVE PROCESSOR:
   Processor ID: 00000000-0000-0000-0000-000000000003
   API Key     : pg_proc_test_demoActvProc_ActiveProcessorDemoSecretKeyForTestingV1234
   Permissions : Approve / Decline Payments and Refunds

4. SUSPENDED PROCESSOR:
   Processor ID: 00000000-0000-0000-0000-000000000004
   API Key     : pg_proc_test_demoSuspProc_SuspendedProcessorDemoSecretKeyTestingV1234
   Permissions : Callback operations return 403 Forbidden
================================================================================
DEMO PROFILE ONLY — NEVER USE THESE CREDENTIALS IN PRODUCTION
================================================================================
```

#### 5. Import Postman Collection & Environment
1. Open **Postman** and click **Import** (top left).
2. Select the two JSON files located in the `postman/` directory:
   - `postman/payment-gateway.postman_collection.json`
   - `postman/payment-gateway-demo.postman_environment.json`
3. In the environment selector dropdown (top right), select **`Payment Gateway Demo Environment`**.

#### 6. Run the Happy Path Flow
In Postman, open folder **`01 - Happy Path`** and execute the requests in sequence:
1. `01 Create Payment — Active Merchant`: Submits a `$50.00` payment. Returns `201 Created` (`PENDING`) and stores `paymentId`.
2. `02 Get Payment — Active Merchant`: Fetches the created payment and confirms `PENDING` state.
3. `03 List Payments — Active Merchant`: Queries paginated history and validates the payment appears in results.
4. `04 Approve Payment — Active Processor`: Processor callback approving the payment (`200 OK`, `APPROVED`).
5. `05 Create Refund — Active Merchant`: Submits a partial refund of `$20.00` against the approved payment. Returns `201 Created` (`PENDING`) and stores `refundId`.
6. `06 Get Refund — Active Merchant`: Verifies the refund details and `PENDING` status.
7. `07 List Refunds — Active Merchant`: Confirms the refund appears in the merchant's refund history.
8. `08 Approve Refund — Active Processor`: Processor callback approving the refund (`200 OK`, `APPROVED`).

---

### Exploring Additional Scenarios

The Postman collection contains 24 structured requests organized across 5 folders:

- **`02 - Idempotency`**: Demonstrates that replaying requests with the same `Idempotency-Key` and payload returns the original entity (`201 Created`), while changing payload parameters under the same key triggers `409 Conflict`.
- **`03 - Security`**: Verifies `401 Unauthorized` for missing or malformed tokens, and `403 Forbidden` when credentials attempt cross-role operations (e.g. a Merchant calling processor callbacks).
- **`04 - Suspended Actors`**: Demonstrates that Suspended Merchants cannot create new payments (`403 Forbidden`) but retain access to transaction history (`200 OK`), and Suspended Processors cannot process callbacks (`403 Forbidden`).
- **`05 - Pagination / Validation`**: Demonstrates robust parameter validation on pagination bounds (negative page, zero size, sizes > 100, malformed types $\to$ `400 Bad Request`).

---

### Resetting Demo Data
To wipe local PostgreSQL data and start with a fresh database:
```bash
docker compose down -v
docker compose up -d
```
*(Rebooting with `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` will reapply migrations and re-seed the demo actors cleanly).*

---

### Running Automated Tests
Execute the full test suite (unit, concurrency, integration, and Testcontainers end-to-end tests):
```bash
./mvnw test
```
The test suite currently contains **436 tests** (0 failures, 0 errors, 0 skipped).

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

**Current Test Baseline**: 436 tests, 0 failures, 0 errors, 0 skipped.

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
