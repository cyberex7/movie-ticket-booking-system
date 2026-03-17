package com.movie.booking.service;

import com.movie.booking.dto.request.CreateShowRequest;
import com.movie.booking.dto.response.ShowDetailsResponse;
import com.movie.booking.entity.Movie;
import com.movie.booking.entity.Screen;
import com.movie.booking.entity.Show;
import com.movie.booking.enums.BookingStatus;
import com.movie.booking.repository.BookingRepository;
import com.movie.booking.repository.MovieRepository;
import com.movie.booking.repository.ScreenRepository;
import com.movie.booking.repository.ShowRepository;
import com.movie.booking.entity.*;
import com.movie.booking.enums.ShowTimeSlot;
import com.movie.booking.exception.InvalidOperationException;
import com.movie.booking.exception.ResourceNotFoundException;
import com.movie.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final BookingRepository bookingRepository;

    // ── Theatre partner write operations ──────────────────────────────────────

    /**
     * Write Scenario 2: Theatre partner creates a show.
     * Validates the partner owns the screen's theatre, then checks for
     * any time conflicts on that screen before persisting.
     */
    @Transactional
    public Show createShow(Long partnerUserId, CreateShowRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie", request.getMovieId()));

        Screen screen = screenRepository.findById(request.getScreenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen", request.getScreenId()));

        assertPartnerOwnsScreen(screen, partnerUserId);

        LocalTime calculatedEndTime = request.getShowTime()
                .plusMinutes(movie.getDurationMinutes())
                .plusMinutes(30); // 30-minute buffer between shows

        List<Show> conflictingShows = showRepository.findConflictingShowsOnScreen(
                request.getScreenId(),
                request.getShowDate(),
                request.getShowTime(),
                calculatedEndTime);

        if (!conflictingShows.isEmpty()) {
            throw new InvalidOperationException(
                    "Show timing conflicts with an existing show on screen: " + screen.getScreenName());
        }

        Show newShow = Show.builder()
                .movie(movie)
                .screen(screen)
                .showDate(request.getShowDate())
                .showTime(request.getShowTime())
                .endTime(calculatedEndTime)
                .timeSlot(deriveTimeSlot(request.getShowTime()))
                .baseTicketPrice(request.getBaseTicketPrice())
                .availableSeatCount(screen.getTotalSeatCapacity())
                .showStatus(Show.ShowStatus.SCHEDULED)
                .isActive(true)
                .build();

        Show savedShow = showRepository.save(newShow);
        log.info("Show created: showId={}, movie={}, screen={}, date={}, time={}",
                savedShow.getShowId(), movie.getMovieTitle(),
                screen.getScreenName(), request.getShowDate(), request.getShowTime());
        return savedShow;
    }

    /**
     * Write Scenario 2: Update show details.
     * Blocked if the show already has confirmed bookings, since changing
     * time or price would be misleading to customers who already booked.
     */
    @Transactional
    public Show updateShow(Long showId, Long partnerUserId, CreateShowRequest request) {
        Show show = findShowById(showId);
        assertPartnerOwnsScreen(show.getScreen(), partnerUserId);
        assertNoConfirmedBookingsExist(showId, "update");

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie", request.getMovieId()));

        LocalTime updatedEndTime = request.getShowTime()
                .plusMinutes(movie.getDurationMinutes())
                .plusMinutes(30);

        show.setShowDate(request.getShowDate());
        show.setShowTime(request.getShowTime());
        show.setEndTime(updatedEndTime);
        show.setTimeSlot(deriveTimeSlot(request.getShowTime()));
        show.setBaseTicketPrice(request.getBaseTicketPrice());

        return showRepository.save(show);
    }

    /**
     * Write Scenario 2: Soft-delete a show by marking it CANCELLED.
     * Blocked if confirmed bookings exist to protect customers.
     */
    @Transactional
    public void deleteShow(Long showId, Long partnerUserId) {
        Show show = findShowById(showId);
        assertPartnerOwnsScreen(show.getScreen(), partnerUserId);
        assertNoConfirmedBookingsExist(showId, "cancel");

        show.setShowStatus(Show.ShowStatus.CANCELLED);
        show.setIsActive(false);
        showRepository.save(show);
        log.info("Show cancelled: showId={}", showId);
    }

    // ── Customer read operations ───────────────────────────────────────────────

    /**
     * Read Scenario 1: Browse theatres currently running a movie in a city
     * on a chosen date, including all show timings.
     */
    public List<ShowDetailsResponse> getShowsForMovieInCity(
            Long movieId, String cityName, LocalDate showDate) {
        log.info("Fetching shows for movieId={} in city={} on date={}", movieId, cityName, showDate);

        return showRepository
                .findScheduledShowsInCityByNameForMovieOnDate(cityName, movieId, showDate)
                .stream()
                .map(this::toShowDetailsResponse)
                .toList();
    }

    public ShowDetailsResponse getShowDetails(Long showId) {
        return toShowDetailsResponse(findShowById(showId));
    }

    public List<ShowDetailsResponse> getShowsByTheatreAndDate(Long theatreId, LocalDate showDate) {
        return showRepository.findScheduledShowsByTheatreAndDate(theatreId, showDate)
                .stream().map(this::toShowDetailsResponse).toList();
    }

    public List<ShowDetailsResponse> getShowsByTheatreMovieAndDate(
            Long theatreId, Long movieId, LocalDate showDate) {
        return showRepository.findScheduledShowsByTheatreAndMovieAndDate(theatreId, movieId, showDate)
                .stream().map(this::toShowDetailsResponse).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    public Show findShowById(Long showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", showId));
    }

    private void assertPartnerOwnsScreen(Screen screen, Long partnerUserId) {
        Long ownerUserId = screen.getTheatre().getTheatrePartner().getUserId();
        if (!ownerUserId.equals(partnerUserId)) {
            throw new InvalidOperationException(
                    "You do not have permission to manage shows on this screen");
        }
    }

    private void assertNoConfirmedBookingsExist(Long showId, String operation) {
        List<BookingStatus> activeStatuses =
                List.of(BookingStatus.CONFIRMED,
                        BookingStatus.PENDING);
        boolean hasBookings = !bookingRepository
                .findByShowShowIdAndBookingStatusIn(showId, activeStatuses).isEmpty();
        if (hasBookings) {
            throw new InvalidOperationException(
                    "Cannot " + operation + " a show that already has confirmed bookings");
        }
    }

    /**
     * Maps a show start time to a named time slot.
     * The AFTERNOON slot boundary is intentionally broad here (12–17)
     * for slot categorisation; the actual discount window (12–16) is
     * controlled separately in AfternoonShowDiscountStrategy.
     */
    private ShowTimeSlot deriveTimeSlot(LocalTime showTime) {
        int hour = showTime.getHour();
        if (hour < 12) return ShowTimeSlot.MORNING;
        if (hour < 17) return ShowTimeSlot.AFTERNOON;
        if (hour < 21) return ShowTimeSlot.EVENING;
        return ShowTimeSlot.NIGHT;
    }

    ShowDetailsResponse toShowDetailsResponse(Show show) {
        return ShowDetailsResponse.builder()
                .showId(show.getShowId())
                .movieId(show.getMovie().getMovieId())
                .movieTitle(show.getMovie().getMovieTitle())
                .theatreName(show.getScreen().getTheatre().getTheatreName())
                .screenName(show.getScreen().getScreenName())
                .cityName(show.getScreen().getTheatre().getCity().getCityName())
                .showDate(show.getShowDate())
                .showTime(show.getShowTime())
                .endTime(show.getEndTime())
                .timeSlot(show.getTimeSlot() != null ? show.getTimeSlot().name() : null)
                .baseTicketPrice(show.getBaseTicketPrice().toPlainString())
                .availableSeatCount(show.getAvailableSeatCount())
                .showStatus(show.getShowStatus().name())
                .build();
    }
}
