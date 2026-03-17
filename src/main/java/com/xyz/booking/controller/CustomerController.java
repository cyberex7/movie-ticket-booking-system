package com.xyz.booking.controller;

import com.xyz.booking.dto.request.BookTicketsRequest;
import com.xyz.booking.dto.request.BulkCancelRequest;
import com.xyz.booking.dto.response.BookingResponse;
import com.xyz.booking.dto.response.PriceCalculationResponse;
import com.xyz.booking.dto.response.SeatResponse;
import com.xyz.booking.dto.response.ShowDetailsResponse;
import com.xyz.booking.entity.Movie;
import com.xyz.booking.entity.Seat;
import com.xyz.booking.entity.Show;
import com.xyz.booking.repository.MovieRepository;
import com.xyz.booking.repository.SeatRepository;
import com.xyz.booking.service.BookingService;
import com.xyz.booking.service.OfferService;
import com.xyz.booking.service.ShowService;
import com.xyz.booking.service.UserResolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Customer-facing REST API — B2C operations.
 * URL prefix: /api/v1/customer  (matches uploaded CustomerController).
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "B2C — browse movies, view shows, book and cancel tickets")
public class CustomerController {

    private final MovieRepository movieRepository;
    private final ShowService showService;
    private final BookingService bookingService;
    private final SeatRepository seatRepository;
    private final OfferService offerService;
    private final UserResolverService userResolverService;

    // ── Browse movies (public) ────────────────────────────────────────────────

    /**
     * Browse active movies with optional filters by city, language, or genre.
     * GET /api/v1/customer/movies
     */
    @GetMapping("/movies")
    @Operation(summary = "Browse movies — filter by city, language, or genre")
    public ResponseEntity<List<Movie>> browseMovies(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String genre) {

        List<Movie> movies;
        if (city != null && !city.isBlank()) {
            movies = movieRepository.findMoviesCurrentlyShowingInCity(city);
        } else if (language != null && !language.isBlank()) {
            movies = movieRepository.findByLanguageIgnoreCaseAndIsActiveTrue(language);
        } else if (genre != null && !genre.isBlank()) {
            movies = movieRepository.findByGenreIgnoreCaseAndIsActiveTrue(genre);
        } else {
            movies = movieRepository.findByIsActiveTrue();
        }
        return ResponseEntity.ok(movies);
    }

    /**
     * Read Scenario 1: theatres currently running a movie in a city on a date,
     * with all show timings sorted by theatre name then show time.
     * GET /api/v1/customer/movies/{movieId}/shows?city=Mumbai&date=2024-02-15
     */
    @GetMapping("/movies/{movieId}/shows")
    @Operation(summary = "Read Scenario 1 — theatres and show timings for a movie in a city on a date")
    public ResponseEntity<List<ShowDetailsResponse>> getShowsForMovieInCity(
            @PathVariable Long movieId,
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(showService.getShowsForMovieInCity(movieId, city, date));
    }

    // ── Seat availability (public) ────────────────────────────────────────────

    /**
     * List available seats for a show — used to render the interactive seat grid.
     * GET /api/v1/customer/shows/{showId}/seats
     */
    @GetMapping("/shows/{showId}/seats")
    @Operation(summary = "Available seats for a show")
    public ResponseEntity<List<SeatResponse>> getAvailableSeats(@PathVariable Long showId) {
        Show show = showService.findShowById(showId);
        List<Seat> availableSeats = seatRepository.findAvailableSeatsForShow(
                show.getScreen().getScreenId(), showId);
        return ResponseEntity.ok(availableSeats.stream().map(this::toSeatResponse).toList());
    }

    // ── Offer preview (public) ────────────────────────────────────────────────

    /**
     * Read Scenario 2: preview the price breakdown with all applicable offers
     * before confirming a booking.
     * POST /api/v1/customer/offers/calculate?showId=1&seatIds=1,2,3
     */
    @PostMapping("/offers/calculate")
    @Operation(summary = "Read Scenario 2 — preview pricing and applicable offers for a seat selection")
    public ResponseEntity<PriceCalculationResponse> previewOfferCalculation(
            @RequestParam Long showId,
            @RequestParam List<Long> seatIds) {

        Show show = showService.findShowById(showId);
        List<Seat> seats = seatRepository.findAllById(seatIds);
        return ResponseEntity.ok(offerService.calculateFinalPrice(show, seats));
    }

    // ── Booking (authenticated) ───────────────────────────────────────────────

    /**
     * Write Scenario 1: book selected seats for a show.
     * Both the afternoon (20%) and third-ticket (50%) discounts fire automatically.
     * POST /api/v1/customer/bookings
     */
    @PostMapping("/bookings")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Write Scenario 1 — book tickets with automatic offer application")
    public ResponseEntity<BookingResponse> bookTickets(
            @Valid @RequestBody BookTicketsRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.bookSelectedTickets(customerUserId, request));
    }

    /**
     * Bulk booking — same transactional flow, expects a larger seat list.
     * POST /api/v1/customer/bookings/bulk
     */
    @PostMapping("/bookings/bulk")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Bulk book — many seats for one show in a single transaction")
    public ResponseEntity<BookingResponse> bulkBookTickets(
            @Valid @RequestBody BookTicketsRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.bulkBookTickets(customerUserId, request));
    }

    /** GET /api/v1/customer/bookings/{bookingReference} */
    @GetMapping("/bookings/{bookingReference}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get booking details by reference number")
    public ResponseEntity<BookingResponse> getBookingDetails(
            @PathVariable String bookingReference,
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.ok(
                bookingService.getBookingByReference(bookingReference, customerUserId));
    }

    /** GET /api/v1/customer/bookings */
    @GetMapping("/bookings")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "My complete booking history")
    public ResponseEntity<List<BookingResponse>> getMyBookingHistory(
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.ok(bookingService.getCustomerBookingHistory(customerUserId));
    }

    /**
     * Write Scenario: cancel a single booking.
     * DELETE /api/v1/customer/bookings/{bookingReference}
     */
    @DeleteMapping("/bookings/{bookingReference}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancel a booking by its reference number")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable String bookingReference,
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.ok(
                bookingService.cancelSingleBooking(bookingReference, customerUserId));
    }

    /**
     * Write Scenario: bulk cancellation.
     * DELETE /api/v1/customer/bookings/bulk
     */
    @DeleteMapping("/bookings/bulk")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Bulk cancel — cancel multiple bookings in one call")
    public ResponseEntity<List<BookingResponse>> bulkCancelBookings(
            @Valid @RequestBody BulkCancelRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long customerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.ok(
                bookingService.cancelMultipleBookings(request, customerUserId));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SeatResponse toSeatResponse(Seat seat) {
        return SeatResponse.builder()
                .seatId(seat.getSeatId())
                .seatNumber(seat.getSeatNumber())
                .rowLabel(seat.getRowLabel())
                .columnNumber(seat.getColumnNumber())
                .seatType(seat.getSeatType().name())
                .ticketPrice(seat.getTicketPrice())
                .seatStatus(seat.getSeatStatus().name())
                .build();
    }
}
