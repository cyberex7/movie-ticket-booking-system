package com.movie.booking.service;

import com.movie.booking.dto.request.BookTicketsRequest;
import com.movie.booking.dto.request.BulkCancelRequest;
import com.movie.booking.dto.response.BookingResponse;
import com.movie.booking.dto.response.PriceCalculationResponse;
import com.movie.booking.entity.*;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.SeatRepository;
import com.movie.booking.repository.ShowRepository;
import com.movie.booking.repository.UserRepository;
import com.movie.booking.entity.*;
import com.movie.booking.enums.BookingStatus;
import com.movie.booking.exception.BookingException;
import com.movie.booking.exception.ResourceNotFoundException;
import com.movie.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final OfferService offerService;

    // ── Write Scenario 1: Book tickets ───────────────────────────────────────

    /**
     * Core booking flow:
     * 1. Validate the show is still accepting bookings
     * 2. Acquire a pessimistic write lock on selected seats — prevents two
     *    concurrent transactions from booking the same seat
     * 3. Verify each locked seat is still available for this show
     * 4. Calculate gross total from per-seat prices (PREMIUM/VIP cost more)
     * 5. Stack all applicable discount strategies via OfferService
     * 6. Persist booking + BookingSeat records atomically
     * 7. Decrement show.availableSeatCount
     */
    @Transactional
    public BookingResponse bookSelectedTickets(Long customerUserId, BookTicketsRequest request) {
        Show show = showRepository.findById(request.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show", request.getShowId()));

        if (show.getShowStatus() == Show.ShowStatus.CANCELLED
                || Boolean.FALSE.equals(show.getIsActive())) {
            throw new BookingException("This show is no longer available for booking");
        }
        if (show.getAvailableSeatCount() < request.getSelectedSeatIds().size()) {
            throw new BookingException("Not enough seats available. Requested: "
                    + request.getSelectedSeatIds().size()
                    + ", Available: " + show.getAvailableSeatCount());
        }

        // Pessimistic lock — concurrent requests for the same seats queue here
        List<Seat> lockedSeats =
                seatRepository.findSeatsByIdWithPessimisticLock(request.getSelectedSeatIds());

        if (lockedSeats.size() != request.getSelectedSeatIds().size()) {
            throw new BookingException("One or more selected seat IDs are invalid");
        }

        validateSeatsAvailableForShow(lockedSeats, show.getShowId(),
                show.getScreen().getScreenId());

        User customer = userRepository.findById(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerUserId));

        // Calculate pricing with all applicable discount strategies stacked
        PriceCalculationResponse pricing = offerService.calculateFinalPrice(show, lockedSeats);

        // Build offer summary string for the booking record
        String offerSummary = pricing.getAppliedOffers().stream()
                .map(PriceCalculationResponse.AppliedOfferDetail::getOfferDescription)
                .collect(Collectors.joining(" | "));

        Booking booking = Booking.builder()
                .bookingReference(generateBookingReference())
                .customer(customer)
                .show(show)
                .numberOfSeats(lockedSeats.size())
                .grossAmount(pricing.getGrossAmount())
                .discountAmount(pricing.getTotalDiscountAmount())
                .netPayableAmount(pricing.getNetPayableAmount())
                .appliedOfferSummary(offerSummary.isEmpty() ? null : offerSummary)
                .bookingStatus(BookingStatus.CONFIRMED)
                .paymentStatus(Booking.PaymentStatus.PENDING)
                .bookingDate(LocalDateTime.now())
                .build();

        booking = bookingRepository.save(booking);

        // Create seat association records with price snapshot
        Booking savedBooking = booking;
        List<BookingSeat> seatRecords = lockedSeats.stream()
                .map(seat -> BookingSeat.builder()
                        .booking(savedBooking)
                        .seat(seat)
                        .ticketPriceAtBooking(seat.getTicketPrice())
                        .build())
                .collect(Collectors.toList());
        booking.setBookedSeats(seatRecords);
        bookingRepository.save(booking);

        // Reduce available count on the show
        show.setAvailableSeatCount(show.getAvailableSeatCount() - lockedSeats.size());
        showRepository.save(show);

        log.info("Booking confirmed: ref={} | seats={} | gross=₹{} | discount=₹{} | net=₹{}",
                booking.getBookingReference(), lockedSeats.size(),
                pricing.getGrossAmount(), pricing.getTotalDiscountAmount(),
                pricing.getNetPayableAmount());

        return toBookingResponse(booking, pricing);
    }

    /**
     * Bulk booking — delegates to the same single-booking flow.
     * One transaction covers all seats in one show.
     */
    @Transactional
    public BookingResponse bulkBookTickets(Long customerUserId, BookTicketsRequest request) {
        // Bulk booking is identical to standard booking; the "bulk" label
        // simply signals that a larger seat list is expected.
        return bookSelectedTickets(customerUserId, request);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String bookingReference, Long customerUserId) {
        Booking booking = findBookingByReference(bookingReference);
        assertCustomerOwnsBooking(booking, customerUserId);
        return toBookingResponse(booking, null);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getCustomerBookingHistory(Long customerUserId) {
        return bookingRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerUserId)
                .stream().map(b -> toBookingResponse(b, null)).toList();
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    @Transactional
    public BookingResponse cancelSingleBooking(String bookingReference, Long customerUserId) {
        Booking booking = findBookingByReference(bookingReference);
        assertCustomerOwnsBooking(booking, customerUserId);
        return performCancellation(booking);
    }

    @Transactional
    public List<BookingResponse> cancelMultipleBookings(
            BulkCancelRequest request, Long customerUserId) {
        return request.getBookingReferences().stream().map(reference -> {
            Booking booking = findBookingByReference(reference);
            assertCustomerOwnsBooking(booking, customerUserId);
            return performCancellation(booking);
        }).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void validateSeatsAvailableForShow(
            List<Seat> seats, Long showId, Long screenId) {
        List<Seat> availableSeats =
                seatRepository.findAvailableSeatsForShow(screenId, showId);
        Set<Long> availableSeatIds = availableSeats.stream()
                .map(Seat::getSeatId).collect(Collectors.toSet());

        List<Long> alreadyTaken = seats.stream()
                .map(Seat::getSeatId)
                .filter(id -> !availableSeatIds.contains(id))
                .toList();
        if (!alreadyTaken.isEmpty()) {
            throw new BookingException("Seats already booked or unavailable: " + alreadyTaken);
        }
    }

    private BookingResponse performCancellation(Booking booking) {
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new BookingException("Booking is already cancelled: "
                    + booking.getBookingReference());
        }
        if (booking.getBookingStatus() == BookingStatus.REFUNDED) {
            throw new BookingException("Booking has already been refunded: "
                    + booking.getBookingReference());
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Restore available seats on the show
        Show show = booking.getShow();
        show.setAvailableSeatCount(
                show.getAvailableSeatCount() + booking.getNumberOfSeats());
        showRepository.save(show);

        log.info("Booking cancelled: ref={}", booking.getBookingReference());
        return toBookingResponse(booking, null);
    }

    private String generateBookingReference() {
        return "BK" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private Booking findBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found for reference: " + reference));
    }

    private void assertCustomerOwnsBooking(Booking booking, Long customerUserId) {
        if (!booking.getCustomer().getUserId().equals(customerUserId)) {
            throw new BookingException("This booking does not belong to your account");
        }
    }

    private BookingResponse toBookingResponse(
            Booking booking, PriceCalculationResponse pricing) {

        List<String> seatNumberList = booking.getBookedSeats() == null
                ? List.of()
                : booking.getBookedSeats().stream()
                        .map(bs -> bs.getSeat().getSeatNumber())
                        .toList();

        List<String> offerNames = (pricing != null && pricing.getAppliedOffers() != null)
                ? pricing.getAppliedOffers().stream()
                        .map(PriceCalculationResponse.AppliedOfferDetail::getOfferName)
                        .toList()
                : (booking.getAppliedOfferSummary() != null
                        ? Arrays.asList(booking.getAppliedOfferSummary().split(" \\| "))
                        : List.of());

        return BookingResponse.builder()
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .showId(booking.getShow().getShowId())
                .movieTitle(booking.getShow().getMovie().getMovieTitle())
                .theatreName(booking.getShow().getScreen().getTheatre().getTheatreName())
                .screenName(booking.getShow().getScreen().getScreenName())
                .cityName(booking.getShow().getScreen().getTheatre().getCity().getCityName())
                .showDate(booking.getShow().getShowDate().toString())
                .showTime(booking.getShow().getShowTime().toString())
                .numberOfSeats(booking.getNumberOfSeats())
                .seatNumbers(seatNumberList)
                .grossAmount(booking.getGrossAmount())
                .discountAmount(booking.getDiscountAmount())
                .netPayableAmount(booking.getNetPayableAmount())
                .bookingStatus(booking.getBookingStatus().name())
                .paymentStatus(booking.getPaymentStatus().name())
                .bookingDate(booking.getBookingDate())
                .appliedOfferNames(offerNames)
                .build();
    }
}
