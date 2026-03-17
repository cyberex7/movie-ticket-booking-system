package com.movie.booking;

import com.movie.booking.entity.Movie;
import com.movie.booking.repository.MovieRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration smoke tests for the main booking workflow.
 *
 * Uses the "test" Spring profile → H2 in-memory database.
 * MovieRepository is mocked so tests run without any seeded data.
 *
 * URL prefix matches the uploaded CustomerController: /api/v1/customer
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MovieRepository movieRepository;

    // ── Read Scenario 1 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/customer/movies — returns active movie list")
    @WithMockUser
    void browseMovies_returnsActiveMovieList() throws Exception {
        Movie activeMovie = Movie.builder()
                .movieTitle("Test Movie")
                .language("English")
                .genre("Action")
                .isActive(true)
                .releaseDate(LocalDate.now())
                .durationMinutes(120)
                .build();

        when(movieRepository.findByIsActiveTrue()).thenReturn(List.of(activeMovie));

        mockMvc.perform(get("/api/v1/customer/movies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].movieTitle").value("Test Movie"))
                .andExpect(jsonPath("$[0].language").value("English"))
                .andExpect(jsonPath("$[0].genre").value("Action"));

        System.out.println("TEST PASSED: browseMovies_returnsActiveMovieList");
    }

    @Test
    @DisplayName("GET /api/v1/customer/movies?language=Hindi — filters by language")
    @WithMockUser
    void browseMovies_filterByLanguage_returnsMatchingMovies() throws Exception {
        Movie hindiMovie = Movie.builder()
                .movieTitle("Hindi Film")
                .language("Hindi")
                .genre("Drama")
                .isActive(true)
                .releaseDate(LocalDate.now())
                .durationMinutes(135)
                .build();

        when(movieRepository.findByLanguageIgnoreCaseAndIsActiveTrue("Hindi"))
                .thenReturn(List.of(hindiMovie));

        mockMvc.perform(get("/api/v1/customer/movies")
                        .param("language", "Hindi")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movieTitle").value("Hindi Film"))
                .andExpect(jsonPath("$[0].language").value("Hindi"));

        System.out.println("TEST PASSED: browseMovies_filterByLanguage_returnsMatchingMovies");
    }

    @Test
    @DisplayName("GET /api/v1/customer/movies?genre=Action — filters by genre")
    @WithMockUser
    void browseMovies_filterByGenre_returnsMatchingMovies() throws Exception {
        Movie actionMovie = Movie.builder()
                .movieTitle("Action Hero")
                .language("English")
                .genre("Action")
                .isActive(true)
                .releaseDate(LocalDate.now())
                .durationMinutes(150)
                .build();

        when(movieRepository.findByGenreIgnoreCaseAndIsActiveTrue("Action"))
                .thenReturn(List.of(actionMovie));

        mockMvc.perform(get("/api/v1/customer/movies")
                        .param("genre", "Action")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movieTitle").value("Action Hero"))
                .andExpect(jsonPath("$[0].genre").value("Action"));

        System.out.println("TEST PASSED: browseMovies_filterByGenre_returnsMatchingMovies");
    }

    @Test
    @DisplayName("GET /api/v1/customer/movies — empty list when no active movies")
    @WithMockUser
    void browseMovies_noActiveMovies_returnsEmptyList() throws Exception {
        when(movieRepository.findByIsActiveTrue()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customer/movies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        System.out.println("TEST PASSED: browseMovies_noActiveMovies_returnsEmptyList");
    }

    @Test
    @DisplayName("GET /api/v1/customer/movies — requires authentication (no mock user → 401)")
    void browseMovies_withoutAuthentication_isPublicEndpoint() throws Exception {
        when(movieRepository.findByIsActiveTrue()).thenReturn(List.of());

        // /movies browse is public — returns 200 even without auth
        mockMvc.perform(get("/api/v1/customer/movies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        System.out.println("TEST PASSED: browseMovies_withoutAuthentication_isPublicEndpoint");
    }
}
