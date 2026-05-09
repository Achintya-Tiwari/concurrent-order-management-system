# Concurrent Order Management System

A backend REST API built with Java 17 and Spring Boot 3, designed around realistic order management workflows. The project covers transactional order processing, inventory tracking, async bill generation, concurrent batch processing, and scheduled SLA monitoring — all packaged with Docker and documented via Swagger.

Built as a portfolio project that reflects how production Spring Boot applications are structured, without over-engineering or buzzword-driven complexity.

---

## Features

- **Order lifecycle management** — place, track, and ship orders with full transactional integrity
- **Inventory management** — stock-aware ordering with quantity validation at purchase time
- **Customer management** — registration, lookup, and order history
- **Async bill generation** — non-blocking invoice creation using `@Async` and `CompletableFuture`
- **Concurrent order processing simulation** — fetch or process multiple orders in parallel via `ExecutorService`
- **Delayed-order scheduler** — nightly cron job that flags PENDING orders past their SLA deadline
- **Reporting APIs** — monthly order counts, monthly revenue, and top customer queries
- **Flyway migrations** — versioned schema management, no manual SQL setup required
- **Swagger / OpenAPI** — fully documented and interactable API out of the box
- **Docker Compose** — one command to run the full stack (app + MySQL)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8.0 |
| Migrations | Flyway |
| Async / Concurrency | `CompletableFuture`, `ExecutorService`, `@Async`, `@Scheduled` |
| API Documentation | Springdoc OpenAPI / Swagger UI |
| Containerisation | Docker, Docker Compose |
| Build Tool | Maven |
| Utilities | Lombok, Bean Validation |

---

## Architecture Overview

The application follows a standard layered architecture: HTTP requests come in through controllers, pass through a service layer that handles business logic and transactions, and persist via JPA repositories to MySQL. A separate async processing layer handles non-blocking and batch workloads without blocking the main request threads.

```
HTTP Request
    │
    ▼
Controller Layer          (REST endpoints, input validation, Swagger annotations)
    │
    ▼
Service Layer             (@Transactional business logic, entity mapping, DTOs)
    │           │
    ▼           ▼
Repository Layer     Async Processing Layer
(JPA / Hibernate)    (@Async, ExecutorService, @Scheduled)
    │
    ▼
MySQL 8.0
```

Everything runs inside Docker, with the app container waiting for MySQL to pass its health check before starting.

---

## Architecture Diagram

```mermaid
graph TD
    A[Client / Swagger UI\nhttp://localhost:8080/swagger-ui/index.html] -->|HTTP REST| B

    subgraph Spring Boot Application
        B[Controller Layer\nOrderController\nCustomerController\nStockItemController\nReportController]

        B -->|calls| C[Service Layer\nOrderService\nCustomerService\nStockItemService\nReportingService]

        C -->|@Async CompletableFuture| D[Async Processing Layer\nBillGeneratorService\nConcurrentOrderProcessor\nDelayedOrderScheduler]

        C -->|Spring Data JPA| E[Repository Layer\nPurchaseOrderRepository\nCustomerRepository\nStockItemRepository\nOrderItemRepository]

        D -->|delegates to| C
    end

    subgraph Thread Pools - AsyncConfig
        F[omsTaskExecutor\ncore=2 max=5 queue=100\nfor @Async bill generation]
        G[omsConcurrentExecutor\nfixed pool size=4\nfor batch order processing]
    end

    D --> F
    D --> G

    subgraph Docker Environment
        E -->|HikariCP connection pool| H[(MySQL 8.0\norder-management_db)]
        I[Flyway\nV1__init_schema.sql] -->|runs on startup| H
    end
```

---

## Module Breakdown

### `controller/`
REST layer. Each controller is thin — it validates input, delegates to the service layer, and maps responses. Annotated with Springdoc tags for automatic Swagger documentation.

| Controller | Responsibility |
|---|---|
| `OrderController` | Full order lifecycle — place, ship, bill, async bill, batch fetch |
| `CustomerController` | Customer CRUD |
| `StockItemController` | Inventory management |
| `ReportController` | Read-only analytical reporting endpoints |

### `service/`
Business logic lives here. All mutating operations use `@Transactional`. Services never expose entities directly — they map everything to DTOs before returning.

| Service | Responsibility |
|---|---|
| `OrderService` | Order placement (with stock deduction), shipping, billing, delayed order query |
| `CustomerService` | Customer registration and lookup |
| `StockItemService` | Inventory CRUD |
| `ReportingService` | Aggregated reporting queries via custom JPQL |

### `async/`
The concurrency showcase of the project.

| Class | Pattern used |
|---|---|
| `BillGeneratorService` | `@Async` + `CompletableFuture` — fire-and-forget bill generation on `omsTaskExecutor` |
| `ConcurrentOrderProcessor` | `ExecutorService` + `CompletableFuture.supplyAsync()` — parallel batch order fetch/processing |
| `DelayedOrderScheduler` | `@Scheduled` cron — nightly delayed-order SLA scan |

### `config/`
| Class | Purpose |
|---|---|
| `AsyncConfig` | Configures two named thread pools: `omsTaskExecutor` (for `@Async`) and `omsConcurrentExecutor` (for `ExecutorService`). Implements `AsyncConfigurer` so any bare `@Async` defaults to the bounded pool instead of Spring's thread-per-call default. |
| `OpenApiConfig` | Swagger UI metadata and API info |

### `entity/`
JPA entities: `Customer`, `PurchaseOrder`, `OrderItem`, `StockItem`, and the `OrderStatus` enum (`PENDING` / `SHIPPED`). The `unit_price` on `OrderItem` is intentionally snapshotted at order time so billing remains accurate even if product prices change later.

### `repository/`
Spring Data JPA interfaces with custom JPQL queries — monthly aggregations, delayed order detection, top customer lookup.

### `exception/`
Centralised error handling via `@RestControllerAdvice`. Custom exceptions (`ResourceNotFoundException`, `BusinessRuleException`, `DuplicateResourceException`) map to appropriate HTTP status codes with a consistent error body.

### `dto/`
Separate `request/` and `response/` packages. Keeps entities away from the API surface and makes the contract explicit.

---

## Async and Concurrency

This project demonstrates three distinct concurrency patterns, each with its own use case:

### 1. `@Async` with `CompletableFuture` — `BillGeneratorService`
```
GET /api/orders/{id}/bill/async
    │
    ▼
Controller returns HTTP 202 immediately
    │
    ▼
BillGeneratorService.generateBillAsync() runs on omsTaskExecutor pool
    │  (background thread, independent of the HTTP response)
    ▼
Bill assembled, logged — result available for polling/callback in a real system
```
The HTTP thread is never blocked. The caller gets a `{ "status": "PROCESSING" }` response instantly.

### 2. `ExecutorService` + batch `CompletableFuture` — `ConcurrentOrderProcessor`
```
POST /api/orders/batch/fetch  →  [orderId1, orderId2, ..., orderIdN]
    │
    ▼
Each order ID → CompletableFuture.supplyAsync(() -> orderService.getOrderById(id), omsConcurrentExecutor)
    │
    ▼
CompletableFuture.allOf(futures).join()  →  collect all results
    │
    ▼
Return list of OrderResponse DTOs (failed fetches excluded, errors logged per-order)
```
Demonstrates why `ExecutorService` is preferred over `@Async` when you need to submit many tasks and collect all their results.

### 3. `@Scheduled` cron — `DelayedOrderScheduler`
Runs at midnight daily (`0 0 0 * * *`). Scans for PENDING orders where `ship_date < today`, logs a summary. The cron expression is externalised to `application.yml` so it can be changed per environment without recompiling (e.g., `0 * * * * *` every minute in dev).

### Thread Pool Design (`AsyncConfig`)

Two pools are configured deliberately:

| Pool | Type | Size | Used for |
|---|---|---|---|
| `omsTaskExecutor` | `ThreadPoolTaskExecutor` | core=2, max=5, queue=100 | Spring `@Async` tasks |
| `omsConcurrentExecutor` | `Executors.newFixedThreadPool` | 4 threads | `ExecutorService` batch processing |

Using a bounded pool instead of Spring's default `SimpleAsyncTaskExecutor` (which creates a new OS thread per call) prevents thread explosion under load and provides backpressure via `CallerRunsPolicy`.

---

## API Summary

Base URL: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### Orders — `/api/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/orders` | Place a new order (deducts stock, `@Transactional`) |
| `GET` | `/api/orders` | List all orders (optional `?customerId=` filter) |
| `GET` | `/api/orders/{id}` | Get a single order |
| `PATCH` | `/api/orders/{id}/ship` | Ship an order (PENDING → SHIPPED) |
| `GET` | `/api/orders/delayed` | List orders past their SLA deadline |
| `GET` | `/api/orders/{id}/bill` | Generate invoice (synchronous) |
| `GET` | `/api/orders/{id}/bill/async` | Trigger async bill generation (HTTP 202) |
| `POST` | `/api/orders/batch/fetch` | Fetch multiple orders concurrently |

### Customers — `/api/customers`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/customers` | Register a customer |
| `GET` | `/api/customers` | List all customers |
| `GET` | `/api/customers/{id}` | Get customer by ID |

### Inventory — `/api/stock-items`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/stock-items` | Add a stock item |
| `GET` | `/api/stock-items` | List all stock items |
| `GET` | `/api/stock-items/{id}` | Get item by ID |

### Reports — `/api/reports`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/reports/monthly-orders` | Shipped order counts grouped by month |
| `GET` | `/api/reports/monthly-revenue` | Revenue from shipped orders grouped by month |
| `GET` | `/api/reports/top-customer` | Customer with the highest order count |

---

## Docker Setup

### Prerequisites
- Docker Desktop (or Docker Engine + Docker Compose)
- No local Java or MySQL installation required

### 1. Create a `.env` file

Copy the example below and save it as `.env` in the project root:

```env
MYSQL_DATABASE=order-management_db
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_USER=omsuser
MYSQL_PASSWORD=omspassword

SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/order-management_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=omsuser
SPRING_DATASOURCE_PASSWORD=omspassword
```

### 2. Build and start

```bash
# Build the JAR first
mvn clean package -DskipTests

# Start MySQL + app (detached)
docker compose up --build -d
```

### 3. Check status

```bash
docker compose ps
docker compose logs oms-backend-app --follow
```

The app container waits for MySQL's health check to pass before starting. Flyway runs on first boot and creates the schema automatically.

### 4. Stop and clean up

```bash
# Stop containers (data persists in the mysql_data volume)
docker compose down

# Stop and remove all data
docker compose down -v
```

---

## Local Development Setup (without Docker)

### Prerequisites
- Java 17 (e.g. Eclipse Temurin)
- Maven 3.8+
- MySQL 8.0 running locally

### Steps

**1. Create the database**
```sql
CREATE DATABASE `order-management_db`;
CREATE USER 'omsuser'@'localhost' IDENTIFIED BY 'omspassword';
GRANT ALL PRIVILEGES ON `order-management_db`.* TO 'omsuser'@'localhost';
FLUSH PRIVILEGES;
```

**2. Configure credentials**

Set environment variables, or update `application-dev.yml`:
```bash
export DB_USERNAME=omsuser
export DB_PASSWORD=omspassword
```

**3. Run the application**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway will apply `V1__init_schema.sql` on first run. No manual table creation needed.

**4. Run tests**
```bash
mvn test
```

---

## Swagger UI

Once the application is running, open:

```
http://localhost:8080/swagger-ui/index.html
```

From the Swagger UI you can:
- Browse all available endpoints grouped by tag (Orders, Customers, Stock Items, Reports)
- Expand any endpoint to see request/response schemas
- Click **Try it out** → fill in parameters → **Execute** to make a live API call
- View the raw OpenAPI spec at `http://localhost:8080/v3/api-docs`

A suggested order for testing from scratch:
1. `POST /api/customers` — create a customer
2. `POST /api/stock-items` — add a product with a quantity
3. `POST /api/orders` — place an order referencing the customer and stock item
4. `GET /api/orders/{id}/bill` — generate a synchronous invoice
5. `GET /api/orders/{id}/bill/async` — observe the non-blocking 202 response
6. `PATCH /api/orders/{id}/ship` — ship the order
7. `GET /api/reports/monthly-revenue` — see the revenue report update

---

## Docker Commands Reference

```bash
# Build image only (no compose)
docker build -t oms-backend .

# Run app container standalone (assumes MySQL is available)
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/order-management_db \
  -e SPRING_DATASOURCE_USERNAME=omsuser \
  -e SPRING_DATASOURCE_PASSWORD=omspassword \
  oms-backend

# Tail app logs
docker logs oms-backend-app -f

# Open a shell inside the running app container
docker exec -it oms-backend-app /bin/bash

# Connect to MySQL container
docker exec -it oms-mysql mysql -u omsuser -p order-management_db

# Rebuild after code changes
mvn clean package -DskipTests
docker compose up --build -d
```

---

## Project Structure

```
oms-backend/
├── Dockerfile
├── docker-compose.yml
├── .env                            # local secrets — not committed to git
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/psl/oms/
    │   │   ├── OmsApplication.java
    │   │   ├── async/
    │   │   │   ├── BillGeneratorService.java
    │   │   │   ├── ConcurrentOrderProcessor.java
    │   │   │   └── DelayedOrderScheduler.java
    │   │   ├── config/
    │   │   │   ├── AsyncConfig.java
    │   │   │   └── OpenApiConfig.java
    │   │   ├── controller/
    │   │   │   ├── CustomerController.java
    │   │   │   ├── OrderController.java
    │   │   │   ├── ReportController.java
    │   │   │   └── StockItemController.java
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── CreateCustomerRequest.java
    │   │   │   │   ├── CreateStockItemRequest.java
    │   │   │   │   └── PlaceOrderRequest.java
    │   │   │   └── response/
    │   │   │       ├── AsyncBillResponse.java
    │   │   │       ├── BillResponse.java
    │   │   │       ├── CustomerResponse.java
    │   │   │       ├── MonthlyOrderResponse.java
    │   │   │       ├── MonthlyRevenueResponse.java
    │   │   │       ├── OrderItemResponse.java
    │   │   │       ├── OrderResponse.java
    │   │   │       ├── StockItemResponse.java
    │   │   │       └── TopCustomerResponse.java
    │   │   ├── entity/
    │   │   │   ├── Customer.java
    │   │   │   ├── OrderItem.java
    │   │   │   ├── OrderStatus.java
    │   │   │   ├── PurchaseOrder.java
    │   │   │   └── StockItem.java
    │   │   ├── exception/
    │   │   │   ├── BusinessRuleException.java
    │   │   │   ├── DuplicateResourceException.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── ResourceNotFoundException.java
    │   │   ├── repository/
    │   │   │   ├── CustomerRepository.java
    │   │   │   ├── OrderItemRepository.java
    │   │   │   ├── PurchaseOrderRepository.java
    │   │   │   └── StockItemRepository.java
    │   │   └── service/
    │   │       ├── CustomerService.java
    │   │       ├── OrderService.java
    │   │       ├── ReportingService.java
    │   │       └── StockItemService.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       └── db/migration/
    │           └── V1__init_schema.sql
    └── test/
        └── java/com/psl/oms/
            ├── async/
            │   ├── BillGeneratorServiceTest.java
            │   ├── ConcurrentOrderProcessorTest.java
            │   └── DelayedOrderSchedulerTest.java
            ├── controller/
            │   └── CustomerControllerTest.java
            ├── entity/
            │   └── EntityRelationshipTest.java
            ├── repository/
            │   └── CustomerRepositoryTest.java
            └── service/
                ├── CustomerServiceTest.java
                └── OrderServiceTest.java
```

---

## Future Improvements

- **Polling endpoint for async bill status** — currently the async bill endpoint returns 202 with no way to retrieve the result. A `/api/orders/{id}/bill/status` endpoint backed by an in-memory or DB store would complete the pattern.
- **`@ConfigurationProperties` for thread pool sizing** — pool sizes are currently constants in `AsyncConfig`. Binding them to typed config classes would make runtime tuning cleaner.
- **Pagination** — list endpoints currently return all records. Adding `Pageable` support to repositories and controllers is a straightforward next step.
- **Database seeding script** — a `V2__seed_data.sql` migration with sample customers and products would make local testing faster.
- **Integration tests** — current tests are unit tests. Adding `@SpringBootTest` tests with Testcontainers for the MySQL layer would give better end-to-end confidence.
- **`@ControllerAdvice` for async errors** — exceptions thrown inside `CompletableFuture` chains are not automatically caught by `GlobalExceptionHandler`. A `Thread.UncaughtExceptionHandler` or explicit `.exceptionally()` handling would improve observability.

---

## Learning Outcomes

Working through this project gave hands-on experience with:

- **Spring Boot project structure** — how controllers, services, repositories, and config classes are organised and why
- **`@Transactional` semantics** — how Spring manages transaction boundaries and what happens when they're violated
- **Spring Data JPA** — writing custom JPQL queries, understanding lazy vs eager loading, and the N+1 problem
- **Flyway** — version-controlled schema management and why it beats `ddl-auto: create`
- **Thread pool configuration** — the difference between `SimpleAsyncTaskExecutor`, `ThreadPoolTaskExecutor`, and raw `ExecutorService`, and when to use each
- **`CompletableFuture` patterns** — `supplyAsync`, `allOf`, `join`, and exception handling in async chains
- **`@Async` vs `ExecutorService`** — practical tradeoffs between Spring's managed async abstraction and direct executor control
- **`@Scheduled`** — running background jobs on a cron schedule and externalising cron expressions to config
- **DTO pattern** — keeping JPA entities out of the API layer and why it matters for maintainability
- **Docker Compose** — containerising a multi-service application and wiring health checks between dependent services
- **OpenAPI / Swagger** — documenting APIs at the code level with annotations and generating interactive docs automatically

## Screenshots

### Swagger UI Home

![Swagger Home](docs/images/swagger-home.png)

### API Endpoints

![API Endpoints](docs/images/apis.png)

### Request / Response Schemas

![Schemas](docs/images/schemas.png)

### Docker Containers Running

![Docker Containers](docs/images/docker-containers.png)