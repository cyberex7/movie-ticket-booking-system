# Movie Booking Platform

A B2B/B2C movie ticket booking platform built with Java 17 and Spring Boot 3.

## Overview

| Audience | What they can do |
|---|---|
| **Theatre Partners (B2B)** | Register theatres, create and manage shows, control seat inventory, view bookings |
| **Customers (B2C)** | Browse movies by city/genre/language, view show timings, book seats, apply offers, cancel bookings |

---

## Technology Stack

| Concern | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.1 |
| Database | MySQL 8 (H2 in-memory for tests) |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT (jjwt 0.12.3) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.8+ |
| Connection pool | HikariCP (20 max connections) |
| Cache | Spring Cache (simple in-memory; swap Redis in production) |

---

## Architecture

### Package structure

```
src/main/java/com/xyz/booking/
├── config/              # SecurityConfig, OpenApiConfig, AuditConfig, DataSeeder
├── controller/          # AuthController, CustomerController, TheatreController
├── dto/
│   ├── request/         # AuthRequest, BookTicketsRequest, CreateShowRequest, BulkCancelRequest
│   └── response/        # AuthResponse, BookingResponse, ShowDetailsResponse,
│                        # PriceCalculationResponse, SeatResponse
├── entity/              # City, User, Theatre, Screen, Movie, Show, Seat, BookingSeat, Booking, Offer
├── enums/               # UserRole, BookingStatus, SeatStatus, ShowTimeSlot
├── exception/           # ResourceNotFoundException, BookingException,
│                        # InvalidOperationException, GlobalExceptionHandler
├── repository/          # One JpaRepository per entity, with custom JPQL queries
├── security/            # JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── service/
    ├── strategy/        # DiscountStrategy (interface), AfternoonShowDiscountStrategy,
    │                    # ThirdTicketDiscountStrategy
    ├── AuthService.java
    ├── BookingService.java
    ├── OfferService.java
    ├── ShowService.java
    └── UserResolverService.java

src/main/resources/
└── application.yml      # MySQL datasource, JWT config, app.offers.* properties, test profile

src/test/java/com/xyz/booking/
└── BookingWorkflowTest.java
```

### Design patterns

**Strategy Pattern — discount calculation**
`OfferService` holds a `List<DiscountStrategy>` injected by Spring. At booking time every strategy's `isApplicable()` is checked and all matching discounts are **stacked** — both the afternoon and third-ticket discounts can fire together on the same booking. Adding a new offer requires only a new `@Component` class; no existing code changes.

```
DiscountStrategy (interface)
  ├── AfternoonShowDiscountStrategy   — 20% off shows 12:00–16:00
  └── ThirdTicketDiscountStrategy     — 50% off every 3rd ticket (price-desc sort)
```

**Repository Pattern**
All data access is behind `JpaRepository` interfaces with named JPQL queries. Business logic in services never builds queries inline.

**DTO Pattern**
Separate request and response classes for every API surface. Entities never leave the service layer.

---

## Data model

```
City ──< Theatre ──< Screen ──< Seat
                       │
                      Show >── Movie
                       │
                    Booking ──< BookingSeat >── Seat
                       │
                      User
```

Key entity decisions:

- **Seats are screen-scoped**, not show-scoped. Seat records are created once when a theatre partner allocates inventory. `BookingSeat` is the join table that links a booking to specific seats for a specific show, with a `ticketPriceAtBooking` snapshot so price changes never affect issued receipts.
- **`@Version` on `Show` and `Booking`** provides optimistic locking against lost-update on the `availableSeatCount` field.
- **Pessimistic write lock** (`PESSIMISTIC_WRITE`) on the seat query inside `BookingService` prevents two concurrent transactions from booking the same seat simultaneously.
- **`Show.ShowStatus`** enum (`SCHEDULED → RUNNING → COMPLETED / CANCELLED`) tracks lifecycle alongside the `isActive` boolean used in query filters.
- **`Booking.PaymentStatus`** (`PENDING / COMPLETED / FAILED / REFUNDED`) is tracked independently from `BookingStatus` so a confirmed booking can still have a pending payment while awaiting a gateway callback.

---

## Prerequisites

- Java 17+
- MySQL 8+
- Maven 3.8+

No Redis required — the current cache type is `simple` (in-memory). Redis configuration is documented in `application.yml` comments for when you are ready to add it.

---

## Getting started

### 1 — Create the MySQL database

```sql
CREATE DATABASE movie_booking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2 — Configure credentials

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/movie_booking?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
```

### 3 — Configure the JWT secret

Replace the default secret with a strong random string of at least 32 characters:

```yaml
jwt:
  secret: your-strong-secret-key-at-least-32-characters
  expiration-ms: 86400000   # 24 hours
```

### 4 — Build and run

```bash
mvn clean install
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.  
On first startup `DataSeeder` runs automatically and populates the database with demo cities, users, theatres, screens, seats, movies, and shows.

### 5 — Swagger UI

```
http://localhost:8080/swagger-ui.html
```

Use **Authorize** (top right) and paste the JWT token from the login response as `Bearer <token>`.

---

## Demo seed data

`DataSeeder` creates the following on first startup (idempotent — skipped if data exists):

| Type | Details |
|---|---|
| Cities | Mumbai, Bangalore, Delhi |
| Users | `customer@example.com`, `partner@example.com`, `admin@example.com` (password: `Password@123`) |
| Theatres | PVR Cinemas — Lower Parel (Mumbai), INOX — Koramangala (Bangalore) |
| Screens | 2 screens per theatre (Screen 1: 100 seats, Screen 2: 80 seats) |
| Seats | Rows A–C → PREMIUM (₹300), Rows D–J → REGULAR (₹200) |
| Movies | The Great Adventure (Hindi/Action), Comedy Nights (Hindi/Comedy), The Silent Thriller (English/Thriller) |
| Shows | Morning (10:00), **Afternoon (14:00)**, Evening (18:00), Night (21:30) — today and tomorrow |

The 14:00 show is in the afternoon window so both discount strategies fire immediately when you test with 3+ seats.

---

## API reference

### Authentication — `/api/v1/auth`

| Method | Path | Body | Description |
|---|---|---|---|
| POST | `/register` | `{ fullName, email, password, phoneNumber, role }` | Register (role: `CUSTOMER` or `THEATRE_PARTNER`) |
| POST | `/login` | `{ email, password }` | Returns `{ accessToken, tokenType, userId, fullName, role }` |

### Customer — `/api/v1/customer`

All browse endpoints are public. Booking endpoints require `Authorization: Bearer <token>`.

| Method | Path | Query params | Description |
|---|---|---|---|
| GET | `/movies` | `city`, `language`, `genre` (all optional) | Browse active movies |
| GET | `/movies/{movieId}/shows` | `city`, `date` | **Read Scenario 1** — theatres and show timings for a movie in a city on a date |
| GET | `/shows/{showId}/seats` | — | Available seats for a show (seat grid) |
| POST | `/offers/calculate` | `showId`, `seatIds` | **Read Scenario 2** — preview pricing with all applicable discounts |
| POST | `/bookings` | — | **Write Scenario 1** — book tickets (discounts applied automatically) |
| POST | `/bookings/bulk` | — | Bulk book — many seats for one show in one transaction |
| GET | `/bookings` | — | My booking history |
| GET | `/bookings/{reference}` | — | Booking detail by reference number |
| DELETE | `/bookings/{reference}` | — | Cancel a booking |
| DELETE | `/bookings/bulk` | — | Bulk cancel |

**Book tickets request body:**
```json
{
  "showId": 1,
  "selectedSeatIds": [10, 11, 12],
  "promoCode": "optional"
}
```

### Theatre partner — `/api/v1/theatre`

All endpoints require `Authorization: Bearer <token>` with role `THEATRE_PARTNER`.

| Method | Path | Description |
|---|---|---|
| POST | `/shows` | **Write Scenario 2** — create a show (checks for scheduling conflicts) |
| PUT | `/shows/{showId}` | Update show date, time, or price (blocked if bookings exist) |
| DELETE | `/shows/{showId}` | Cancel a show (blocked if bookings exist) |
| GET | `/shows` | View shows at a theatre on a date (`?theatreId=&date=`) |
| GET | `/shows/{showId}/seats` | View full seat layout and statuses |
| PUT | `/shows/{showId}/seats` | **Write Scenario 3** — bulk update seat status (`?seatIds=&newStatus=BLOCKED`) |
| GET | `/shows/{showId}/bookings` | View all bookings for a show |

**Create show request body:**
```json
{
  "movieId": 1,
  "screenId": 1,
  "showDate": "2024-06-15",
  "showTime": "14:30",
  "baseTicketPrice": 250.00
}
```

---

## Offer implementation

Both offers are configured in `application.yml` and applied automatically — no promo code required:

```yaml
app:
  offers:
    third-ticket-discount-percent: 50
    afternoon-discount-percent: 20
    afternoon-start-hour: 12
    afternoon-end-hour: 16
```

**Third ticket discount (50% off every 3rd seat)**
Seats are sorted by price descending before applying the discount so the highest-priced 3rd seat always gets discounted.

Example — 5 seats `[₹300, ₹300, ₹300, ₹200, ₹200]` sorted desc:
- Position 3: ₹300 → 50% off = **₹150 discount**
- Position 6: does not exist
- Total discount: ₹150

**Afternoon show discount (20% off total)**
Applies when `showTime >= afternoonStartHour` and `showTime < afternoonEndHour`.

**Stacking**
Both discounts fire independently and are **added together**. A 3-ticket booking at 14:00 gets both:

```
Gross:    ₹900  (3 × ₹300)
3rd seat: −₹150 (50% of ₹300)
Afternoon:−₹180 (20% of ₹900)
Net:      ₹570
```

---

## Running tests

```bash
# Run all tests (uses H2 in-memory, no MySQL needed)
mvn test

# Run with test profile explicitly
mvn test -Dspring.profiles.active=test
```

`BookingWorkflowTest` covers five scenarios against `GET /api/v1/customer/movies` using `@MockBean MovieRepository` and `@WithMockUser`, running entirely in-memory.

---

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `jwt.secret` | (set this) | HS256 signing key — minimum 32 characters |
| `jwt.expiration-ms` | `86400000` | Token lifetime (24 hours) |
| `app.offers.third-ticket-discount-percent` | `50` | % off every 3rd ticket |
| `app.offers.afternoon-discount-percent` | `20` | % off afternoon shows |
| `app.offers.afternoon-start-hour` | `12` | Afternoon window start (inclusive) |
| `app.offers.afternoon-end-hour` | `16` | Afternoon window end (exclusive) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Use `validate` in production with Flyway |

---

## Known build note — Lombok on Java 21

If you encounter `java.lang.ExceptionInInitializerError: TypeTag::UNKNOWN` at compile time, it means Lombok is being run in an isolated annotation processor classloader where it cannot see the javac internals. The `pom.xml` in this project is already configured to avoid this:

- Lombok version is managed by the Spring Boot BOM via `<lombok.version>1.18.34</lombok.version>` in `<properties>` — no `<version>` tag in the dependency block.
- There is **no** `<annotationProcessorPaths>` block in the compiler plugin. Lombok is picked up from the regular compile classpath (`scope=provided`) through `META-INF/services`, which puts it in the same classloader as javac and resolves the error.

---

## Future enhancements

- Payment gateway integration (Razorpay / Stripe)
- Email and SMS booking confirmations
- Seat hold with expiry timer (reserve for 10 minutes before payment)
- Redis cache for movie catalogue and show schedules
- Flyway database migrations for production deployments
- Dynamic pricing based on occupancy
- Analytics dashboard for theatre partners

---

## Contact

support@xyz.com
