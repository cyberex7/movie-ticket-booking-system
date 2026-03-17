# API Testing Guide

Step-by-step instructions for testing all implemented scenarios of the Movie Booking Platform.

## Prerequisites

- Application running on `http://localhost:8080`
- MySQL database running with the credentials in `application.yml`
- On first startup, `DataSeeder` populates the database automatically — no SQL scripts needed
- Postman, curl, or Swagger UI

## Swagger UI

```
http://localhost:8080/swagger-ui.html
```

After logging in, click **Authorize** (top right) and enter `Bearer <your_token>`.

---

## Authentication

All booking endpoints require a JWT token. The theatre partner endpoints additionally require the `THEATRE_PARTNER` role. IDs are **numeric** (`Long`), not UUIDs.

### Step 1 — Register (if needed)

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "email": "customer@example.com",
    "password": "Password@123",
    "phoneNumber": "9876543210",
    "role": "CUSTOMER"
  }'
```

### Step 2 — Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer@example.com",
    "password": "Password@123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "fullName": "John Doe",
  "email": "customer@example.com",
  "role": "CUSTOMER"
}
```

Save the `accessToken`. All authenticated requests use:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Demo accounts (seeded on startup)

| Role | Email | Password |
|---|---|---|
| Customer | `customer@example.com` | `Password@123` |
| Theatre Partner | `partner@example.com` | `Password@123` |
| Admin | `admin@example.com` | `Password@123` |

---

## Seeded data reference

`DataSeeder` creates the following automatically on first startup. Use these values in the test calls below — replace `{showId}`, `{seatId}` etc. with actual IDs returned from your responses, since they are auto-incremented by MySQL.

| Data | Details |
|---|---|
| Cities | Mumbai (id 1), Bangalore (id 2), Delhi (id 3) |
| Theatres | **PVR Cinemas — Lower Parel** (Mumbai), **INOX — Koramangala** (Bangalore) |
| Screens | Screen 1 (100 seats), Screen 2 (80 seats) per theatre |
| Seat pricing | Rows A–C → PREMIUM ₹300, Rows D–J → REGULAR ₹200 |
| Movies | The Great Adventure (Hindi/Action, 150 min), Comedy Nights (Hindi/Comedy, 120 min), The Silent Thriller (English/Thriller, 140 min) |
| Shows today | PVR: 10:00 (₹200), **14:00 (₹250)**, 18:00 (₹300), 21:30 (₹350) |
| Shows tomorrow | INOX: 11:00 (₹220), **14:30 (₹270)**, 19:00 (₹320) |

The **14:00** and **14:30** shows fall in the afternoon discount window (12:00–16:00), so both discount strategies fire when 3+ seats are selected.

---

## Read Scenario 1 — Browse theatres with show timings

Get all theatres showing a movie in a city on a given date, with full show timing details.

```bash
# Get shows for movie id=1 (The Great Adventure) in Mumbai today
curl "http://localhost:8080/api/v1/customer/movies/1/shows?city=Mumbai&date=$(date +%Y-%m-%d)"
```

**Expected response** (array sorted by theatre name, then show time):
```json
[
  {
    "showId": 2,
    "movieId": 1,
    "movieTitle": "The Great Adventure",
    "theatreName": "PVR Cinemas — Lower Parel",
    "screenName": "Screen 1",
    "cityName": "Mumbai",
    "showDate": "2024-06-15",
    "showTime": "10:00:00",
    "endTime": "12:50:00",
    "timeSlot": "MORNING",
    "baseTicketPrice": "200.00",
    "availableSeatCount": 100,
    "showStatus": "SCHEDULED"
  },
  {
    "showId": 3,
    "showTime": "14:00:00",
    "timeSlot": "AFTERNOON",
    "baseTicketPrice": "250.00",
    ...
  }
]
```

**Verification:**
- Theatre name, screen name, and city included
- Multiple shows returned per theatre when applicable
- `timeSlot` reflects the correct window (MORNING / AFTERNOON / EVENING / NIGHT)

---

## Read Scenario 2 — Preview pricing with offers

Preview the final price before booking. No authentication required.

Use `GET /api/v1/customer/shows/{showId}/seats` first to get available seat IDs.

```bash
# Get available seats for show id=2 (14:00 show)
curl "http://localhost:8080/api/v1/customer/shows/2/seats"
```

Pick three seat IDs from the response, then calculate pricing:

```bash
# Offer preview — 3 REGULAR seats (₹200 each) on the afternoon show
curl -X POST "http://localhost:8080/api/v1/customer/offers/calculate?showId=2&seatIds=1,2,3"
```

### Test Case 1 — Third ticket discount only (non-afternoon show, 3 seats)

Use show id=1 (10:00 morning show, ₹200/seat):

```bash
curl -X POST "http://localhost:8080/api/v1/customer/offers/calculate?showId=1&seatIds=1,2,3"
```

**Expected response:**
```json
{
  "grossAmount": 600.00,
  "totalDiscountAmount": 100.00,
  "netPayableAmount": 500.00,
  "appliedOffers": [
    {
      "offerName": "THIRD_TICKET_DISCOUNT",
      "offerCode": "THIRD_TICKET_DISCOUNT",
      "discountType": "PERCENTAGE",
      "discountAmount": 100.00,
      "offerDescription": "50% off on every 3rd ticket"
    }
  ]
}
```

Calculation: 3 × ₹200 = ₹600. Seats sorted by price desc: [₹200, ₹200, ₹200]. Position 3 gets 50% off = ₹100 discount.

### Test Case 2 — Afternoon discount only (afternoon show, 2 seats)

```bash
curl -X POST "http://localhost:8080/api/v1/customer/offers/calculate?showId=2&seatIds=1,2"
```

**Expected response:**
```json
{
  "grossAmount": 500.00,
  "totalDiscountAmount": 100.00,
  "netPayableAmount": 400.00,
  "appliedOffers": [
    {
      "offerName": "AFTERNOON_SHOW_DISCOUNT",
      "discountAmount": 100.00,
      "offerDescription": "20% off for shows between 12:00 and 16:00"
    }
  ]
}
```

Calculation: 2 × ₹250 = ₹500. 20% of ₹500 = ₹100 discount.

### Test Case 3 — Both discounts stacked (afternoon show, 3 seats)

```bash
curl -X POST "http://localhost:8080/api/v1/customer/offers/calculate?showId=2&seatIds=1,2,3"
```

**Expected response:**
```json
{
  "grossAmount": 750.00,
  "totalDiscountAmount": 275.00,
  "netPayableAmount": 475.00,
  "appliedOffers": [
    {
      "offerName": "THIRD_TICKET_DISCOUNT",
      "discountAmount": 125.00,
      "offerDescription": "50% off on every 3rd ticket"
    },
    {
      "offerName": "AFTERNOON_SHOW_DISCOUNT",
      "discountAmount": 150.00,
      "offerDescription": "20% off for shows between 12:00 and 16:00"
    }
  ]
}
```

Calculation: 3 × ₹250 = ₹750. Third ticket (position 3, price-desc sorted): 50% of ₹250 = ₹125. Afternoon: 20% of ₹750 = ₹150. Both stack: ₹125 + ₹150 = ₹275 total discount.

---

## Write Scenario 1 — Book tickets

Requires `Authorization: Bearer <customer_token>`.

### Step 1 — Get available seats

```bash
curl "http://localhost:8080/api/v1/customer/shows/2/seats"
```

Response lists seats with their `seatId`, `seatNumber`, `seatType`, `ticketPrice`, and `seatStatus`. Note three seat IDs to book.

### Step 2 — Book tickets

```bash
curl -X POST http://localhost:8080/api/v1/customer/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <customer_token>" \
  -d '{
    "showId": 2,
    "selectedSeatIds": [1, 2, 3]
  }'
```

**Expected response (201 Created):**
```json
{
  "bookingId": 1,
  "bookingReference": "BK17199234501A2B",
  "showId": 2,
  "movieTitle": "The Great Adventure",
  "theatreName": "PVR Cinemas — Lower Parel",
  "screenName": "Screen 1",
  "cityName": "Mumbai",
  "showDate": "2024-06-15",
  "showTime": "14:00:00",
  "numberOfSeats": 3,
  "seatNumbers": ["A1", "A2", "A3"],
  "grossAmount": 750.00,
  "discountAmount": 275.00,
  "netPayableAmount": 475.00,
  "bookingStatus": "CONFIRMED",
  "paymentStatus": "PENDING",
  "bookingDate": "2024-06-15T10:30:00",
  "appliedOfferNames": [
    "THIRD_TICKET_DISCOUNT",
    "AFTERNOON_SHOW_DISCOUNT"
  ]
}
```

Save the `bookingReference` — you need it to fetch or cancel the booking.

### Step 3 — Verify seats are now booked

```bash
curl "http://localhost:8080/api/v1/customer/shows/2/seats"
```

Seats 1, 2, 3 should no longer appear in the available list.

### Step 4 — Test double booking prevention

Try booking the same seats again simultaneously from two terminals:

```bash
# Run in two terminals at the same time
curl -X POST http://localhost:8080/api/v1/customer/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <customer_token>" \
  -d '{"showId": 2, "selectedSeatIds": [4]}'
```

One request succeeds with `201`. The other gets `409 Conflict`:
```json
{
  "status": 409,
  "error": "Booking Error",
  "message": "Seats already booked or unavailable: [4]"
}
```

### Step 5 — Bulk booking

```bash
curl -X POST http://localhost:8080/api/v1/customer/bookings/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <customer_token>" \
  -d '{
    "showId": 2,
    "selectedSeatIds": [5, 6, 7, 8, 9]
  }'
```

Same transactional flow — all 5 seats are locked, discounts applied (positions 3 gets 50% off, afternoon fires), and a single booking record is created.

---

## Write Scenario 2 — Theatre partner show management

Login as the theatre partner first:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "partner@example.com", "password": "Password@123"}'
```

Save the `accessToken` from this response as `<partner_token>`.

### Create a show

```bash
curl -X POST http://localhost:8080/api/v1/theatre/shows \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <partner_token>" \
  -d '{
    "movieId": 1,
    "screenId": 1,
    "showDate": "2024-06-20",
    "showTime": "15:00:00",
    "baseTicketPrice": 280.00
  }'
```

**Expected (201 Created):** Full `Show` entity with `showId`, `showStatus: SCHEDULED`, `availableSeatCount: 100`.

**Test overlap prevention** — create another show that overlaps on the same screen:

```bash
curl -X POST http://localhost:8080/api/v1/theatre/shows \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <partner_token>" \
  -d '{
    "movieId": 2,
    "screenId": 1,
    "showDate": "2024-06-20",
    "showTime": "16:00:00",
    "baseTicketPrice": 200.00
  }'
```

**Expected (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Invalid Operation",
  "message": "Show timing conflicts with an existing show on screen: Screen 1"
}
```

### Update a show

Only allowed when no confirmed bookings exist for the show.

```bash
curl -X PUT http://localhost:8080/api/v1/theatre/shows/{showId} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <partner_token>" \
  -d '{
    "movieId": 1,
    "screenId": 1,
    "showDate": "2024-06-20",
    "showTime": "16:30:00",
    "baseTicketPrice": 300.00
  }'
```

**Expected (200 OK):** Updated show with new time and price.

**Test blocked update** — after a customer books seats for this show, try updating:

**Expected (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Invalid Operation",
  "message": "Cannot update a show that already has confirmed bookings"
}
```

### Delete (cancel) a show

```bash
curl -X DELETE http://localhost:8080/api/v1/theatre/shows/{showId} \
  -H "Authorization: Bearer <partner_token>"
```

**Expected (204 No Content).** The show's `showStatus` is set to `CANCELLED` and `isActive` to `false`.

**Test blocked delete** — after bookings exist:

**Expected (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Invalid Operation",
  "message": "Cannot cancel a show that already has confirmed bookings"
}
```

### View shows at a theatre

```bash
curl "http://localhost:8080/api/v1/theatre/shows?theatreId=1&date=$(date +%Y-%m-%d)" \
  -H "Authorization: Bearer <partner_token>"
```

### View bookings for a show

```bash
curl "http://localhost:8080/api/v1/theatre/shows/{showId}/bookings" \
  -H "Authorization: Bearer <partner_token>"
```

---

## Write Scenario 3 — Seat inventory management

Block specific seats (maintenance, VIP reservation, etc.):

```bash
curl -X PUT "http://localhost:8080/api/v1/theatre/shows/{showId}/seats?seatIds=10,11,12&newStatus=BLOCKED" \
  -H "Authorization: Bearer <partner_token>"
```

**Expected (200 OK):**
```
Updated 3 seat(s) to status: BLOCKED
```

Unblock them:

```bash
curl -X PUT "http://localhost:8080/api/v1/theatre/shows/{showId}/seats?seatIds=10,11,12&newStatus=AVAILABLE" \
  -H "Authorization: Bearer <partner_token>"
```

View full seat layout with current statuses:

```bash
curl "http://localhost:8080/api/v1/theatre/shows/{showId}/seats" \
  -H "Authorization: Bearer <partner_token>"
```

---

## Booking management (customer)

### View booking history

```bash
curl http://localhost:8080/api/v1/customer/bookings \
  -H "Authorization: Bearer <customer_token>"
```

### Get booking by reference

```bash
curl http://localhost:8080/api/v1/customer/bookings/BK17199234501A2B \
  -H "Authorization: Bearer <customer_token>"
```

### Cancel a booking

```bash
curl -X DELETE http://localhost:8080/api/v1/customer/bookings/BK17199234501A2B \
  -H "Authorization: Bearer <customer_token>"
```

**Expected (200 OK):** Same booking response body with `bookingStatus: CANCELLED`. The seats are released and `availableSeatCount` on the show increases accordingly.

### Bulk cancel

```bash
curl -X DELETE http://localhost:8080/api/v1/customer/bookings/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <customer_token>" \
  -d '{
    "bookingReferences": ["BK17199234501A2B", "BK17199234502C3D"],
    "cancellationReason": "Change of plans"
  }'
```

---

## Browse movies (public — no auth)

```bash
# All active movies
curl "http://localhost:8080/api/v1/customer/movies"

# Filter by city (shows only movies currently running in that city)
curl "http://localhost:8080/api/v1/customer/movies?city=Mumbai"

# Filter by language
curl "http://localhost:8080/api/v1/customer/movies?language=Hindi"

# Filter by genre
curl "http://localhost:8080/api/v1/customer/movies?genre=Action"
```

---

## Error response reference

All errors return a structured `ErrorResponse`:

```json
{
  "timestamp": "2024-06-15T10:30:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more request fields are invalid",
  "validationErrors": {
    "showId": "Show ID is required",
    "selectedSeatIds": "At least one seat must be selected"
  }
}
```

| Scenario | HTTP status | `error` field |
|---|---|---|
| Seat already booked | 409 | `Booking Error` |
| Show has bookings, can't update/delete | 400 | `Invalid Operation` |
| Show time conflict | 400 | `Invalid Operation` |
| Show / movie / seat not found | 404 | `Not Found` |
| Missing / invalid request fields | 400 | `Validation Failed` |
| Wrong credentials | 401 | `Unauthorized` |
| Accessing another user's booking | 409 | `Booking Error` |
| Theatre partner accessing customer endpoint | 403 | `Forbidden` |

---

## Summary checklist

### Read scenarios
- [ ] Browse movies — all, by city, by language, by genre
- [ ] Read Scenario 1 — theatres and show timings for a movie in a city on a date
- [ ] Read Scenario 2 — offer preview, third-ticket discount only
- [ ] Read Scenario 2 — offer preview, afternoon discount only
- [ ] Read Scenario 2 — offer preview, both discounts stacked

### Write scenarios
- [ ] Write Scenario 1 — book tickets, discounts applied automatically
- [ ] Write Scenario 1 — bulk book
- [ ] Write Scenario 1 — double booking prevention (concurrent requests)
- [ ] Write Scenario 2 — create show
- [ ] Write Scenario 2 — overlap prevention
- [ ] Write Scenario 2 — update show
- [ ] Write Scenario 2 — update blocked when bookings exist
- [ ] Write Scenario 2 — delete (cancel) show
- [ ] Write Scenario 2 — delete blocked when bookings exist
- [ ] Write Scenario 3 — block seats
- [ ] Write Scenario 3 — unblock seats
- [ ] Booking cancellation — single
- [ ] Booking cancellation — bulk