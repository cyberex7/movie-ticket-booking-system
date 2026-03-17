package com.movie.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read Scenario 1 response: one entry per show, grouped implicitly by theatre
 * name in the sorted list returned by ShowService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowDetailsResponse {
    private Long showId;
    private Long movieId;
    private String movieTitle;
    private String theatreName;
    private String screenName;
    private String cityName;
    private LocalDate showDate;
    private LocalTime showTime;
    private LocalTime endTime;
    private String timeSlot;
    private String baseTicketPrice;
    private Integer availableSeatCount;
    private String showStatus;
}
