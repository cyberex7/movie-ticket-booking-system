package com.movie.booking.config;

import com.movie.booking.entity.*;
import com.movie.booking.enums.ShowTimeSlot;
import com.movie.booking.repository.*;
import com.movie.booking.entity.*;
import com.movie.booking.enums.SeatStatus;
import com.movie.booking.enums.UserRole;
import com.movie.booking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the database with realistic demo data on application startup.
 * Only runs when the active profile is NOT "test" — tests use their own
 * MockBean or @DataJpaTest context.
 *
 * Idempotent: if data already exists the seeder skips silently.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("DataSeeder: data already present — skipping.");
            return;
        }

        log.info("DataSeeder: seeding demo data ...");

        List<City> cities       = seedCities();
        List<User> users        = seedUsers();
        List<Theatre> theatres  = seedTheatres(cities, users);
        List<Screen> screens    = seedScreens(theatres);
                                  seedSeats(screens);
        List<Movie> movies      = seedMovies();
                                  seedShows(screens, movies);

        log.info("DataSeeder: complete — {} cities, {} users, {} theatres, {} screens, {} movies.",
                cities.size(), users.size(), theatres.size(), screens.size(), movies.size());
    }

    // ── Cities ────────────────────────────────────────────────────────────────

    private List<City> seedCities() {
        List<City> cities = List.of(
                City.builder().cityName("Mumbai").stateName("Maharashtra")
                        .countryName("India").timezone("Asia/Kolkata").build(),
                City.builder().cityName("Bangalore").stateName("Karnataka")
                        .countryName("India").timezone("Asia/Kolkata").build(),
                City.builder().cityName("Delhi").stateName("Delhi")
                        .countryName("India").timezone("Asia/Kolkata").build()
        );
        return cityRepository.saveAll(cities);
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        String encodedPassword = passwordEncoder.encode("Password@123");

        List<User> users = List.of(
                User.builder()
                        .fullName("John Doe")
                        .email("customer@hotmail.com")
                        .passwordHash(encodedPassword)
                        .phoneNumber("9876543210")
                        .role(UserRole.CUSTOMER)
                        .isActive(true).build(),

                User.builder()
                        .fullName("Theatre Owner")
                        .email("partner@ookmymovie..com")
                        .passwordHash(encodedPassword)
                        .phoneNumber("9876543211")
                        .role(UserRole.THEATRE_PARTNER)
                        .isActive(true).build(),

                User.builder()
                        .fullName("Platform Admin")
                        .email("admin@bookmymovie.com")
                        .passwordHash(encodedPassword)
                        .phoneNumber("9876543212")
                        .role(UserRole.ADMIN)
                        .isActive(true).build()
        );
        return userRepository.saveAll(users);
    }

    // ── Theatres ──────────────────────────────────────────────────────────────

    private List<Theatre> seedTheatres(List<City> cities, List<User> users) {
        City mumbai    = cities.get(0);
        City bangalore = cities.get(1);
        User partner   = users.get(1);   // THEATRE_PARTNER

        List<Theatre> theatres = List.of(
                Theatre.builder()
                        .theatreName("PVR Cinemas — Lower Parel")
                        .theatrePartner(partner)
                        .city(mumbai)
                        .streetAddress("High Street Phoenix, Lower Parel, Mumbai")
                        .stateName("Maharashtra")
                        .pincode("400013")
                        .contactPhone("022-12345678")
                        .isActive(true).build(),

                Theatre.builder()
                        .theatreName("INOX — Koramangala")
                        .theatrePartner(partner)
                        .city(bangalore)
                        .streetAddress("Forum Mall, Koramangala, Bangalore")
                        .stateName("Karnataka")
                        .pincode("560034")
                        .contactPhone("080-87654321")
                        .isActive(true).build()
        );
        return theatreRepository.saveAll(theatres);
    }

    // ── Screens ───────────────────────────────────────────────────────────────

    private List<Screen> seedScreens(List<Theatre> theatres) {
        List<Screen> screens = new ArrayList<>();
        for (Theatre theatre : theatres) {
            screens.add(Screen.builder()
                    .theatre(theatre).screenName("Screen 1").totalSeatCapacity(100).build());
            screens.add(Screen.builder()
                    .theatre(theatre).screenName("Screen 2").totalSeatCapacity(80).build());
        }
        return screenRepository.saveAll(screens);
    }

    // ── Seats ─────────────────────────────────────────────────────────────────

    /**
     * Generates a realistic seat layout for each screen:
     *   Rows A–C  → PREMIUM  (1.5× base price placeholder ₹300)
     *   Rows D–J  → REGULAR  (1.0× base price placeholder ₹200)
     * Actual ticket prices are overwritten when a show is created,
     * but seed values allow the seat grid to display prices immediately.
     */
    private void seedSeats(List<Screen> screens) {
        List<Seat> allSeats = new ArrayList<>();
        String[] premiumRows = {"A", "B", "C"};
        String[] regularRows = {"D", "E", "F", "G", "H", "I", "J"};

        for (Screen screen : screens) {
            int seatsPerRow = screen.getTotalSeatCapacity()
                    / (premiumRows.length + regularRows.length);
            seatsPerRow = Math.max(seatsPerRow, 10);

            for (String row : premiumRows) {
                for (int col = 1; col <= seatsPerRow; col++) {
                    allSeats.add(Seat.builder()
                            .screen(screen)
                            .seatNumber(row + col)
                            .rowLabel(row)
                            .columnNumber(col)
                            .seatType(Seat.SeatType.PREMIUM)
                            .ticketPrice(new BigDecimal("300.00"))
                            .seatStatus(SeatStatus.AVAILABLE)
                            .build());
                }
            }
            for (String row : regularRows) {
                for (int col = 1; col <= seatsPerRow; col++) {
                    allSeats.add(Seat.builder()
                            .screen(screen)
                            .seatNumber(row + col)
                            .rowLabel(row)
                            .columnNumber(col)
                            .seatType(Seat.SeatType.REGULAR)
                            .ticketPrice(new BigDecimal("200.00"))
                            .seatStatus(SeatStatus.AVAILABLE)
                            .build());
                }
            }
        }
        seatRepository.saveAll(allSeats);
        log.info("DataSeeder: {} seats created across {} screens.",
                allSeats.size(), screens.size());
    }

    // ── Movies ────────────────────────────────────────────────────────────────

    private List<Movie> seedMovies() {
        List<Movie> movies = List.of(
                Movie.builder()
                        .movieTitle("The Great Adventure")
                        .movieDescription("An epic action-adventure journey across continents.")
                        .durationMinutes(150)
                        .language("Hindi")
                        .genre("Action")
                        .releaseDate(LocalDate.now().minusDays(10))
                        .ageRating("UA")
                        .isActive(true).build(),

                Movie.builder()
                        .movieTitle("Comedy Nights")
                        .movieDescription("A laugh-out-loud family comedy everyone will enjoy.")
                        .durationMinutes(120)
                        .language("Hindi")
                        .genre("Comedy")
                        .releaseDate(LocalDate.now().minusDays(5))
                        .ageRating("U")
                        .isActive(true).build(),

                Movie.builder()
                        .movieTitle("The Silent Thriller")
                        .movieDescription("A psychological thriller that keeps you guessing till the end.")
                        .durationMinutes(140)
                        .language("English")
                        .genre("Thriller")
                        .releaseDate(LocalDate.now().minusDays(3))
                        .ageRating("A")
                        .isActive(true).build()
        );
        return movieRepository.saveAll(movies);
    }

    // ── Shows ─────────────────────────────────────────────────────────────────

    /**
     * Creates shows for today and tomorrow covering all four time slots.
     * The 14:00 show is deliberately in the afternoon window (12–16)
     * so the AfternoonShowDiscountStrategy fires immediately in demos.
     * The 3-ticket demo uses any show with ≥3 seats selected.
     */
    private void seedShows(List<Screen> screens, List<Movie> movies) {
        LocalDate today    = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        Screen screen1 = screens.get(0);   // PVR Screen 1
        Screen screen2 = screens.get(2);   // INOX Screen 1
        Movie  movie1  = movies.get(0);    // The Great Adventure
        Movie  movie2  = movies.get(1);    // Comedy Nights

        List<Show> shows = new ArrayList<>();

        // Morning show — 10:00
        shows.add(buildShow(screen1, movie1, today,    LocalTime.of(10, 0), new BigDecimal("200.00")));
        // Afternoon show — 14:00  (triggers 20% afternoon discount)
        shows.add(buildShow(screen1, movie1, today,    LocalTime.of(14, 0), new BigDecimal("250.00")));
        // Evening show — 18:00
        shows.add(buildShow(screen1, movie2, today,    LocalTime.of(18, 0), new BigDecimal("300.00")));
        // Night show — 21:30
        shows.add(buildShow(screen1, movie2, today,    LocalTime.of(21, 30), new BigDecimal("350.00")));

        // Tomorrow's shows on INOX screen
        shows.add(buildShow(screen2, movie1, tomorrow, LocalTime.of(11, 0), new BigDecimal("220.00")));
        shows.add(buildShow(screen2, movie1, tomorrow, LocalTime.of(14, 30), new BigDecimal("270.00")));
        shows.add(buildShow(screen2, movie2, tomorrow, LocalTime.of(19, 0), new BigDecimal("320.00")));

        showRepository.saveAll(shows);
        log.info("DataSeeder: {} shows created.", shows.size());
    }

    private Show buildShow(Screen screen, Movie movie,
                           LocalDate date, LocalTime startTime,
                           BigDecimal baseTicketPrice) {
        LocalTime endTime = startTime.plusMinutes(movie.getDurationMinutes()).plusMinutes(30);
        ShowTimeSlot slot = deriveTimeSlot(startTime);

        return Show.builder()
                .movie(movie)
                .screen(screen)
                .showDate(date)
                .showTime(startTime)
                .endTime(endTime)
                .timeSlot(slot)
                .baseTicketPrice(baseTicketPrice)
                .availableSeatCount(screen.getTotalSeatCapacity())
                .showStatus(Show.ShowStatus.SCHEDULED)
                .isActive(true)
                .build();
    }

    private ShowTimeSlot deriveTimeSlot(LocalTime time) {
        int hour = time.getHour();
        if (hour < 12) return ShowTimeSlot.MORNING;
        if (hour < 17) return ShowTimeSlot.AFTERNOON;
        if (hour < 21) return ShowTimeSlot.EVENING;
        return ShowTimeSlot.NIGHT;
    }
}
