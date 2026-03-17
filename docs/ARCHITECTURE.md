# Movie Booking Platform — Architecture Documentation

## Executive Summary

A B2B/B2C movie ticket booking platform built on Spring Boot 3 and MySQL. Theatre partners onboard via a JWT-secured REST API and manage shows on their screens. Customers browse movies by city, preview offer pricing, and book seats with discounts applied automatically. All booking operations are transactionally safe under concurrent load using a combination of pessimistic locking and optimistic versioning.

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Client Layer                           │
│          (Web browser, Mobile app, Postman / curl)            │
└──────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│               Spring Security — JWT Filter Chain              │
│   JwtAuthenticationFilter  →  SecurityContextHolder          │
└──────────────────────────────────────────────────────────────┘
                               │
               ┌───────────────┼───────────────┐
               ▼               ▼               ▼
      ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
      │ AuthController│ │  Customer    │ │  Theatre     │
      │ /api/v1/auth  │ │  Controller  │ │  Controller  │
      │               │ │/api/v1/      │ │ /api/v1/     │
      │               │ │customer      │ │ theatre      │
      └──────────────┘ └──────────────┘ └──────────────┘
               │               │               │
               └───────────────┼───────────────┘
                               ▼
      ┌────────────────────────────────────────────────┐
      │                  Service Layer                  │
      ├────────────────────────────────────────────────┤
      │  AuthService       ShowService                  │
      │  BookingService    OfferService                 │
      │  UserResolverService                            │
      │                                                 │
      │  strategy/                                      │
      │    AfternoonShowDiscountStrategy                │
      │    ThirdTicketDiscountStrategy                  │
      └────────────────────────────────────────────────┘
                               │
      ┌────────────────────────────────────────────────┐
      │               Repository Layer                  │
      ├────────────────────────────────────────────────┤
      │  UserRepository        TheatreRepository        │
      │  MovieRepository       ShowRepository           │
      │  ScreenRepository      SeatRepository           │
      │  BookingRepository     OfferRepository          │
      │  CityRepository                                 │
      └────────────────────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────┐
                    │   MySQL 8       │
                    │  (HikariCP 20)  │
                    └─────────────────┘
```

### Layer responsibilities

**Controller layer** — three controllers, all under `/api/v1`:

| Controller | Prefix | Auth required |
|---|---|---|
| `AuthController` | `/auth` | None (public) |
| `CustomerController` | `/customer` | JWT for booking endpoints; browse/offers are public |
| `TheatreController` | `/theatre` | JWT with `ROLE_THEATRE_PARTNER` on every endpoint |

Controllers extract the authenticated user via `@AuthenticationPrincipal UserDetails` and delegate to `UserResolverService.resolveCurrentUserId()` to get the database `Long` userId. No `?partnerId=` or `?customerId=` query params — identity always comes from the token.

**Service layer** — all business logic, validation, and transaction boundaries live here. Controllers never call repositories directly.

**Repository layer** — one `JpaRepository` per entity, with named JPQL queries for complex lookups. No native SQL; everything goes through the ORM.

---

## Package structure

```
com.xyz.booking/
├── config/
│   ├── AuditConfig.java            — enables @CreationTimestamp / @UpdateTimestamp
│   ├── DataSeeder.java             — seeds demo data on startup (skips if data exists)
│   ├── OpenApiConfig.java          — Swagger / SpringDoc config with JWT security scheme
│   └── SecurityConfig.java         — stateless JWT filter chain, role-based path rules
│
├── controller/
│   ├── AuthController.java         — /register, /login
│   ├── CustomerController.java     — browse, offers, bookings (B2C)
│   └── TheatreController.java      — show CRUD, seat inventory (B2B)
│
├── dto/
│   ├── request/
│   │   ├── AuthRequest.java        — Register (inner), Login (inner)
│   │   ├── BookTicketsRequest.java — showId, selectedSeatIds, promoCode
│   │   ├── BulkCancelRequest.java  — bookingReferences[], cancellationReason
│   │   └── CreateShowRequest.java  — movieId, screenId, showDate, showTime, baseTicketPrice
│   └── response/
│       ├── AuthResponse.java       — accessToken, tokenType, userId, role
│       ├── BookingResponse.java    — full receipt incl. seatNumbers, grossAmount, netPayableAmount
│       ├── PriceCalculationResponse.java — grossAmount, totalDiscountAmount, appliedOffers[]
│       ├── SeatResponse.java       — seatId, seatNumber, seatType, ticketPrice, seatStatus
│       └── ShowDetailsResponse.java — showId, theatreName, showTime, availableSeatCount
│
├── entity/
│   ├── City.java                   — cityId, cityName, stateName, countryName
│   ├── User.java                   — userId, email, passwordHash, role, isActive, @Version
│   ├── Theatre.java                — theatreId, theatreName, city (FK), theatrePartner (FK), coordinates
│   ├── Screen.java                 — screenId, theatre (FK), screenName, totalSeatCapacity
│   ├── Movie.java                  — movieId, movieTitle, language, genre, durationMinutes, isActive
│   ├── Show.java                   — showId, movie (FK), screen (FK), showDate, showTime,
│   │                                  baseTicketPrice, availableSeatCount, ShowStatus, @Version
│   ├── Seat.java                   — seatId, screen (FK), seatNumber, SeatType, ticketPrice,
│   │                                  SeatStatus, reservedAt/Until, @Version
│   ├── BookingSeat.java            — bookingSeatId, booking (FK), seat (FK), ticketPriceAtBooking
│   ├── Booking.java                — bookingId, customer (FK), show (FK), numberOfSeats,
│   │                                  grossAmount, discountAmount, netPayableAmount,
│   │                                  BookingStatus, PaymentStatus, @Version
│   └── Offer.java                  — offerId, offerCode, DiscountType, isStackable, scope FKs
│
├── enums/
│   ├── BookingStatus.java          — PENDING, CONFIRMED, CANCELLED, REFUNDED
│   ├── SeatStatus.java             — AVAILABLE, RESERVED, BOOKED, BLOCKED
│   ├── ShowTimeSlot.java           — MORNING, AFTERNOON, EVENING, NIGHT
│   └── UserRole.java               — CUSTOMER, THEATRE_PARTNER, ADMIN
│
├── exception/
│   ├── BookingException.java       — seat conflicts, ownership violations (409 Conflict)
│   ├── InvalidOperationException.java — business rule violations (400 Bad Request)
│   ├── ResourceNotFoundException.java — entity not found (404 Not Found)
│   └── GlobalExceptionHandler.java — @RestControllerAdvice; returns structured ErrorResponse
│
├── repository/                     — 9 JpaRepository interfaces with custom JPQL
│
├── security/
│   ├── JwtAuthenticationFilter.java — extracts + validates bearer token on each request
│   ├── JwtTokenProvider.java        — generates and validates HS256 JWT tokens (jjwt 0.12.3)
│   └── UserDetailsServiceImpl.java  — loads UserDetails by email for Spring Security
│
└── service/
    ├── AuthService.java            — register, login, token generation
    ├── BookingService.java         — full booking + cancellation lifecycle
    ├── OfferService.java           — stacks all applicable DiscountStrategy implementations
    ├── ShowService.java            — show CRUD, overlap guard, Read Scenario 1
    ├── UserResolverService.java    — maps JWT email principal to database userId
    └── strategy/
        ├── DiscountStrategy.java           — interface: isApplicable, calculateDiscount,
        │                                     getStrategyName, getStrategyDescription
        ├── AfternoonShowDiscountStrategy.java — 20% off shows 12:00–16:00
        └── ThirdTicketDiscountStrategy.java   — 50% off every 3rd ticket (price-desc sort)
```

---

## Design patterns

### 1. Strategy Pattern — discount calculation

`OfferService` receives all `DiscountStrategy` implementations as a `List<DiscountStrategy>` injected by Spring. Every `@Component` that implements the interface is discovered automatically at startup. At pricing time, every strategy is evaluated independently; all applicable discounts are **stacked** (added together), not competed.

```java
// DiscountStrategy.java
public interface DiscountStrategy {
    boolean isApplicable(Show show, List<Seat> selectedSeats);
    BigDecimal calculateDiscount(Show show, List<Seat> selectedSeats, BigDecimal grossAmount);
    String getStrategyName();
    String getStrategyDescription();
}

// OfferService.java — loop never changes when new strategies are added
for (DiscountStrategy strategy : discountStrategies) {
    if (strategy.isApplicable(show, selectedSeats)) {
        BigDecimal discount = strategy.calculateDiscount(show, selectedSeats, grossAmount);
        totalDiscount = totalDiscount.add(discount);
    }
}
```

**Adding a new offer:** create a new `@Component` class implementing `DiscountStrategy`, configure its percentage in `application.yml`. Zero changes to `OfferService`, `BookingService`, or any controller.

**Third-ticket strategy detail:** seats are sorted by `ticketPrice` descending before iterating, so the highest-priced seat at index 2 (0-based) always receives the discount — customers always get the maximum benefit:

```java
List<Seat> sortedByPriceDesc = selectedSeats.stream()
        .sorted(Comparator.comparing(Seat::getTicketPrice).reversed())
        .toList();

for (int index = 2; index < sortedByPriceDesc.size(); index += 3) {
    // 50% off seat at position 3, 6, 9, ...
}
```

**Afternoon strategy detail:** both time boundaries are read from `application.yml` via `@Value`, making the window configurable without recompiling:

```java
@Value("${app.offers.afternoon-start-hour:12}")
private int afternoonStartHour;

@Value("${app.offers.afternoon-end-hour:16}")
private int afternoonEndHour;
```

### 2. Repository Pattern

All data access is behind `JpaRepository` interfaces. Services never build queries inline. Custom JPQL queries are defined as named methods or annotated with `@Query`, making them testable in isolation via `@DataJpaTest`.

Key query: Read Scenario 1 — shows for a movie in a city on a date, ordered by theatre then time:

```java
@Query("""
    SELECT s FROM Show s
    JOIN s.screen sc
    JOIN sc.theatre t
    WHERE LOWER(t.city.cityName) = LOWER(:cityName)
      AND s.movie.movieId = :movieId
      AND s.showDate = :showDate
      AND s.isActive = true
    ORDER BY t.theatreName, s.showTime
    """)
List<Show> findScheduledShowsInCityByNameForMovieOnDate(...);
```

### 3. DTO Pattern

Entities never leave the service layer. Every API surface has a dedicated request DTO (with Bean Validation annotations) and a response DTO. This decouples the internal domain model from the API contract — entity field renames or relationship changes don't break client code.

```java
// Request — validated at the controller boundary
public class BookTicketsRequest {
    @NotNull(message = "Show ID is required")
    private Long showId;

    @NotEmpty(message = "At least one seat must be selected")
    private List<Long> selectedSeatIds;

    private String promoCode;
}

// Response — computed fields, price snapshot
public class BookingResponse {
    private String bookingReference;
    private BigDecimal grossAmount;
    private BigDecimal netPayableAmount;
    private List<String> appliedOfferNames;
    // ...
}
```

### 4. Builder Pattern (hand-written)

Since Lombok is excluded from the project, every entity and response DTO exposes a hand-written static inner `Builder` class following the same fluent API Lombok would have generated. Example from `Booking`:

```java
Booking booking = Booking.builder()
        .bookingReference(generateBookingReference())
        .customer(customer)
        .show(show)
        .numberOfSeats(lockedSeats.size())
        .grossAmount(pricing.getGrossAmount())
        .netPayableAmount(pricing.getNetPayableAmount())
        .bookingStatus(BookingStatus.CONFIRMED)
        .paymentStatus(Booking.PaymentStatus.PENDING)
        .bookingDate(LocalDateTime.now())
        .build();
```

---

## Key design decisions

### 1. Concurrency — pessimistic + optimistic locking combined

**Problem:** Multiple customers booking the same seat simultaneously leads to double-booking.

**Solution — two layers:**

**Layer 1: Pessimistic write lock on seats** (`SeatRepository`)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.seatId IN :seatIds")
List<Seat> findSeatsByIdWithPessimisticLock(@Param("seatIds") List<Long> seatIds);
```

When `bookSelectedTickets()` begins, a `SELECT ... FOR UPDATE` is issued on the requested seats. Any concurrent transaction requesting the same seat IDs blocks at the database level until the first transaction commits or rolls back. The second transaction then re-evaluates availability and rejects the already-booked seats.

**Layer 2: `@Version` optimistic locking on `Show` and `Booking`**

`Show.availableSeatCount` is decremented at the end of each booking. If two transactions both read the same `version` value and both try to commit, one will receive an `OptimisticLockException` (Hibernate retries or surfaces as a 409). This prevents the available-count from going negative even in edge cases where the pessimistic lock has been released.

**Why not optimistic locking alone?** Under high load, many transactions would read seats as available, proceed all the way to discount calculation, then fail at commit. The pessimistic lock means only one transaction does meaningful work per seat batch at a time; all others queue at the lock rather than doing wasteful computation.

### 2. Seat model — screen-scoped, not show-scoped

The uploaded design placed seats in a per-show table, creating a new row for every seat in every show. Our implementation keeps seats on the screen:

| Approach | Show-scoped seats | Screen-scoped seats (our choice) |
|---|---|---|
| Rows created | seats × shows | seats × screens (one-time) |
| Availability check | `seat.status = AVAILABLE` | subquery on `BookingSeat` |
| Price per show | stored on seat row | stored on `BookingSeat` price snapshot |
| Supports different prices per show | requires duplicating seats | via `baseTicketPrice` on Show |

Available seats for a show are computed dynamically:

```java
// SeatRepository — finds seats not linked to any PENDING/CONFIRMED booking for this show
SELECT s FROM Seat s
WHERE s.screen.screenId = :screenId
  AND s.seatStatus = 'AVAILABLE'
  AND s.seatId NOT IN (
      SELECT bs.seat.seatId FROM BookingSeat bs
      WHERE bs.booking.show.showId = :showId
        AND bs.booking.bookingStatus IN ('PENDING','CONFIRMED')
  )
```

`BookingSeat.ticketPriceAtBooking` snapshots the price at the time of purchase, so a later price change on the show does not retroactively affect issued receipts.

### 3. Show lifecycle and overlap guard

`Show` carries a `ShowStatus` enum (`SCHEDULED → RUNNING → COMPLETED / CANCELLED`) alongside a boolean `isActive` used in query predicates. `ShowService.createShow()` enforces a 30-minute buffer and runs an overlap check before saving:

```java
LocalTime calculatedEndTime = request.getShowTime()
        .plusMinutes(movie.getDurationMinutes())
        .plusMinutes(30);  // buffer

List<Show> conflicting = showRepository.findConflictingShowsOnScreen(
        request.getScreenId(), request.getShowDate(),
        request.getShowTime(), calculatedEndTime);

if (!conflicting.isEmpty()) {
    throw new InvalidOperationException(
            "Show timing conflicts with an existing show on screen: " + screen.getScreenName());
}
```

The overlap query uses a standard interval intersection test: `existingStart < proposedEnd AND existingEnd > proposedStart`.

Update and delete are both blocked if confirmed or pending bookings exist for the show, protecting customers from seeing changes to shows they have already paid for.

### 4. Authentication — stateless JWT

Every request carries a `Bearer` token in the `Authorization` header. `JwtAuthenticationFilter` intercepts each request before the controller, validates the token via `JwtTokenProvider` (HS256, jjwt 0.12.3), and populates `SecurityContextHolder`. Sessions are never created (`SessionCreationPolicy.STATELESS`).

The partner identity is extracted from the token — no `?partnerId=` query param is accepted. `UserResolverService.resolveCurrentUserId()` converts the email subject from the JWT to the database `Long` userId on every authenticated call.

### 5. Payment status decoupled from booking status

`Booking` carries two independent status fields:

- `BookingStatus` — `PENDING → CONFIRMED → CANCELLED / REFUNDED` — tracks the booking lifecycle.
- `PaymentStatus` — `PENDING → COMPLETED → FAILED / REFUNDED` — tracks the payment gateway lifecycle.

A booking is set to `CONFIRMED` immediately on a successful seat lock, while `PaymentStatus` remains `PENDING` until a payment gateway callback arrives. This allows the platform to confirm seat reservation instantly without waiting for payment processing.

---

## Data model

```
City ──────────< Theatre >──────────── User (THEATRE_PARTNER)
                    │
                    ├──────────< Screen >──────────── Seat
                    │                                   │
                    └────────── Show >────── Movie      │
                                  │                     │
                                  └─── Booking >── BookingSeat
                                          │
                                        User (CUSTOMER)
```

### Entity decisions

| Entity | Key decisions |
|---|---|
| `User` | Single table for all roles. `passwordHash` stored, never plain text. `@Version` for optimistic lock. |
| `Theatre` | City FK for structured query; also stores `latitude`/`longitude` for future map integrations. Partner identity via FK to `User`. |
| `Screen` | Unique constraint on `(theatre_id, screen_name)`. `totalSeatCapacity` drives `availableSeatCount` on new shows. |
| `Show` | Unique constraint on `(screen_id, show_date, show_time)`. `ShowStatus` enum + `isActive` boolean. `@Version` guards concurrent seat-count decrements. |
| `Seat` | `SeatType` enum (`REGULAR / PREMIUM / VIP`) with `priceMultiplier()` method. `ticketPrice` set at allocation time. `reservedAt / reservedUntil` ready for future hold-expiry. |
| `BookingSeat` | Join table between `Booking` and `Seat`. Stores `ticketPriceAtBooking` as an immutable snapshot. |
| `Booking` | `grossAmount`, `discountAmount`, `netPayableAmount` all stored. `appliedOfferSummary` stores a pipe-separated description for receipt display. Dual status fields for booking and payment lifecycle. |

### Indexes

All frequently queried columns have explicit `@Index` annotations, which Hibernate uses when creating the schema:

```
idx_shows_movie_date   → (movie_id, show_date)
idx_seats_screen_status → (screen_id, seat_status)
idx_bookings_customer  → (user_id)
idx_bookings_reference → (booking_reference)
idx_theatres_active    → (is_active)
```

---

## Non-functional requirements

### Performance

- **HikariCP connection pool** — 20 max connections, 5 minimum idle, configured in `application.yml`.
- **Indexes** on all foreign keys and frequently filtered columns (see above).
- **`@Transactional(readOnly = true)`** on all read-only service methods — Hibernate skips dirty checking and the database can route to a read replica.
- **Cache** — `spring.cache.type=simple` (in-memory) currently. Redis can be substituted by changing one property and adding the `spring-boot-starter-data-redis` dependency; the `@Cacheable` annotations are designed to be drop-in compatible.

### Scalability

- **Stateless services** — no server-side session. Any number of instances can run behind a load balancer.
- **MySQL** can be scaled with read replicas; read-heavy browse queries would benefit most.

### Security

- **JWT HS256** — secret configured via `jwt.secret` in `application.yml` (minimum 32 characters recommended).
- **BCrypt** password hashing via `BCryptPasswordEncoder`.
- **Role-based access** enforced at two levels: `SecurityConfig` path rules and `@PreAuthorize("hasRole('THEATRE_PARTNER')")` on `TheatreController`.
- **Bean Validation** on all request DTOs — `@NotNull`, `@NotEmpty`, `@Email`, `@Size`, `@DecimalMin`, `@FutureOrPresent`. Validation errors return structured `ErrorResponse` with per-field messages.
- **No Lombok** — the `TypeTag::UNKNOWN` annotation-processor classloader issue on Java 21 is avoided entirely by writing constructors, getters, setters, and builders by hand.

### Reliability

- **`@Transactional`** on all write operations — Hibernate rolls back the full unit of work on any unchecked exception.
- **`GlobalExceptionHandler`** centralises all exception-to-HTTP-status mapping. Clients always receive a structured `ErrorResponse` with `timestamp`, `status`, `error`, `message`, and optionally `validationErrors`.
- **`DataSeeder`** is idempotent — checks `userRepository.count() > 0` and skips if data exists, so restarts never corrupt seed data.

---

## Testing strategy

### Current coverage

`BookingWorkflowTest` — Spring Boot integration test using `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@MockBean MovieRepository`. Runs against H2 in-memory database (activated by `@ActiveProfiles("test")`). Covers five scenarios for `GET /api/v1/customer/movies` with and without filters.

### Recommended additions

**Unit tests — discount strategies**
Both strategies are pure functions with no Spring dependencies. Test them directly:

```java
@Test
void thirdTicket_threeSeatsAtSamePrice_discountsThirdSeat() {
    List<Seat> seats = List.of(seat(300), seat(300), seat(300));
    BigDecimal discount = strategy.calculateDiscount(show, seats, new BigDecimal("900"));
    assertThat(discount).isEqualByComparingTo("150.00"); // 50% of 300
}

@Test
void afternoon_showAt1400_isApplicable() {
    Show show = showAt(LocalTime.of(14, 0));
    assertThat(strategy.isApplicable(show, seats)).isTrue();
}

@Test
void afternoon_showAt1600_isNotApplicable() {
    Show show = showAt(LocalTime.of(16, 0));
    assertThat(strategy.isApplicable(show, seats)).isFalse(); // end hour is exclusive
}
```

**Integration tests — booking service**

```java
@DataJpaTest  // or @SpringBootTest with @Transactional
void bookTickets_concurrentRequests_onlyOneSucceeds() {
    // submit two CompletableFutures targeting the same seat
    // assert exactly one BookingStatus.CONFIRMED, one BookingException
}
```

**Controller tests**

```java
@WebMvcTest(CustomerController.class)
@WithMockUser(roles = "CUSTOMER")
void bookTickets_missingShowId_returns400WithFieldErrors() {
    mockMvc.perform(post("/api/v1/customer/bookings")
            .content("{\"selectedSeatIds\": [1,2,3]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.showId").value("Show ID is required"));
}
```

---

## Deployment architecture

```
┌──────────────────┐
│   Load Balancer  │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
┌───┴───┐ ┌───┴───┐
│ App 1 │ │ App 2 │   ← Stateless Spring Boot instances
└───┬───┘ └───┬───┘     (any number, horizontal scale)
    │         │
    └────┬────┘
         │
    ┌────┴─────────────────────┐
    │                          │
┌───┴────┐             ┌───────┴──────┐
│ MySQL  │             │ Redis cache  │
│(primary│             │ (future)     │
│+ read  │             └──────────────┘
│replica)│
└────────┘
```

Spring Boot is fully stateless — JWT tokens carry session state, HikariCP manages the connection pool per instance. Adding a second instance requires no code changes.

---

## Future enhancements

- **Payment gateway** — Razorpay / Stripe integration; `PaymentStatus` field already present on `Booking`
- **Seat hold with expiry** — `reservedAt` and `reservedUntil` fields are already on `Seat`; a scheduled job can expire stale holds
- **Redis caching** — movie catalogue (`TTL` 1 hour), show schedules (`TTL` 15 min), seat availability grid (`TTL` 1 min)
- **Flyway migrations** — replace `ddl-auto: update` with versioned SQL scripts for production deployments
- **Email / SMS notifications** — async via Spring `@EventListener` on `BookingConfirmedEvent`
- **Analytics dashboard** — occupancy rates, revenue per screen, peak booking windows
- **Dynamic pricing** — `baseTicketPrice` on `Show` is already per-show; a pricing engine can update it based on demand without schema changes