package com.movie.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
    private Long seatId;
    private String seatNumber;
    private String rowLabel;
    private Integer columnNumber;
    private String seatType;
    private BigDecimal ticketPrice;
    private String seatStatus;
}
