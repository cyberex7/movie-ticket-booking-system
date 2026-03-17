package com.movie.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Write Scenario 1 response: full booking confirmation sent back to the customer.
 * Includes pricing breakdown, seat numbers, and every applied offer name
 * so the customer receipt is self-contained.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String bookingReference;
    private Long showId;
    private String movieTitle;
    private String theatreName;
    private String screenName;
    private String cityName;
    private String showDate;
    private String showTime;
    private Integer numberOfSeats;
    private List<String> seatNumbers;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal netPayableAmount;
    private String bookingStatus;
    private String paymentStatus;
    private LocalDateTime bookingDate;
    private List<String> appliedOfferNames;
}
