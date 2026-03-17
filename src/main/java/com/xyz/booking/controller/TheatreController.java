package com.xyz.booking.controller;

import com.xyz.booking.dto.request.CreateShowRequest;
import com.xyz.booking.dto.response.ShowDetailsResponse;
import com.xyz.booking.dto.response.SeatResponse;
import com.xyz.booking.entity.Booking;
import com.xyz.booking.entity.Seat;
import com.xyz.booking.entity.Show;
import com.xyz.booking.enums.SeatStatus;
import com.xyz.booking.repository.BookingRepository;
import com.xyz.booking.repository.SeatRepository;
import com.xyz.booking.service.ShowService;
import com.xyz.booking.service.UserResolverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Theatre partner REST API — B2B operations.
 * URL prefix: /api/v1/theatre  (matches uploaded TheatreController).
 * Partner identity is extracted from the JWT token — no partnerId query param needed.
 */
@RestController
@RequestMapping("/api/v1/theatre")
@RequiredArgsConstructor
@PreAuthorize("hasRole('THEATRE_PARTNER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Theatre Partner", description = "B2B — create and manage shows, view seat inventory and bookings")
public class TheatreController {

    private final ShowService showService;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final UserResolverService userResolverService;

    // ── Write Scenario 2: Show CRUD ───────────────────────────────────────────

    /**
     * Create a new show.
     * POST /api/v1/theatre/shows
     * Validates partner owns the target screen, checks for scheduling conflicts.
     */
    @PostMapping("/shows")
    @Operation(summary = "Write Scenario 2 — create a new show for a screen")
    public ResponseEntity<Show> createShow(
            @Valid @RequestBody CreateShowRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long partnerUserId = userResolverService.resolveCurrentUserId(principal);
        Show createdShow = showService.createShow(partnerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdShow);
    }

    /**
     * Update an existing show's date, time, or price.
     * PUT /api/v1/theatre/shows/{showId}
     * Blocked if the show already has confirmed bookings.
     */
    @PutMapping("/shows/{showId}")
    @Operation(summary = "Write Scenario 2 — update show details (blocked if bookings exist)")
    public ResponseEntity<Show> updateShow(
            @PathVariable Long showId,
            @Valid @RequestBody CreateShowRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        Long partnerUserId = userResolverService.resolveCurrentUserId(principal);
        return ResponseEntity.ok(showService.updateShow(showId, partnerUserId, request));
    }

    /**
     * Cancel a show (soft-delete).
     * DELETE /api/v1/theatre/shows/{showId}
     * Blocked if the show has confirmed bookings.
     */
    @DeleteMapping("/shows/{showId}")
    @Operation(summary = "Write Scenario 2 — cancel a show (blocked if bookings exist)")
    public ResponseEntity<Void> deleteShow(
            @PathVariable Long showId,
            @AuthenticationPrincipal UserDetails principal) {

        Long partnerUserId = userResolverService.resolveCurrentUserId(principal);
        showService.deleteShow(showId, partnerUserId);
        return ResponseEntity.noContent().build();
    }

    // ── Write Scenario 3: Seat inventory management ───────────────────────────

    /**
     * Bulk update seat status — block, unblock, or mark for maintenance.
     * PUT /api/v1/theatre/shows/{showId}/seats?seatIds=1,2,3&newStatus=BLOCKED
     */
    @PutMapping("/shows/{showId}/seats")
    @Operation(summary = "Write Scenario 3 — update seat status in bulk (AVAILABLE / BLOCKED / RESERVED)")
    public ResponseEntity<String> updateSeatInventory(
            @PathVariable Long showId,
            @RequestParam @NotEmpty List<Long> seatIds,
            @RequestParam @NotNull SeatStatus newStatus) {

        int updatedCount = seatRepository.bulkUpdateSeatStatus(seatIds, newStatus);
        return ResponseEntity.ok("Updated " + updatedCount + " seat(s) to status: " + newStatus);
    }

    // ── Read: show and booking visibility ─────────────────────────────────────

    /**
     * View all shows at a theatre on a given date.
     * GET /api/v1/theatre/shows?theatreId=1&date=2024-02-15
     */
    @GetMapping("/shows")
    @Operation(summary = "View all shows at a theatre on a date")
    public ResponseEntity<List<ShowDetailsResponse>> getShowsByTheatreAndDate(
            @RequestParam Long theatreId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(showService.getShowsByTheatreAndDate(theatreId, date));
    }

    /**
     * View all bookings made for a specific show.
     * GET /api/v1/theatre/shows/{showId}/bookings
     */
    @GetMapping("/shows/{showId}/bookings")
    @Operation(summary = "View all bookings for a show")
    public ResponseEntity<List<Booking>> getBookingsForShow(@PathVariable Long showId) {
        List<com.xyz.booking.enums.BookingStatus> activeStatuses =
                List.of(com.xyz.booking.enums.BookingStatus.CONFIRMED,
                        com.xyz.booking.enums.BookingStatus.PENDING,
                        com.xyz.booking.enums.BookingStatus.CANCELLED);
        return ResponseEntity.ok(
                bookingRepository.findByShowShowIdAndBookingStatusIn(showId, activeStatuses));
    }

    /**
     * View the current seat layout and status for a show.
     * GET /api/v1/theatre/shows/{showId}/seats
     */
    @GetMapping("/shows/{showId}/seats")
    @Operation(summary = "View all seats and their current status for a show")
    public ResponseEntity<List<SeatResponse>> getSeatsForShow(@PathVariable Long showId) {
        Show show = showService.findShowById(showId);
        List<Seat> seats = seatRepository.findByScreenScreenId(show.getScreen().getScreenId());
        return ResponseEntity.ok(seats.stream().map(this::toSeatResponse).toList());
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
