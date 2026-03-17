package com.movie.booking.repository;

import com.movie.booking.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByIsActiveTrue();

    List<Movie> findByLanguageIgnoreCaseAndIsActiveTrue(String language);

    List<Movie> findByGenreIgnoreCaseAndIsActiveTrue(String genre);

    /**
     * All active movies currently showing in a city — used by customer browse.
     */
    @Query("""
        SELECT DISTINCT m FROM Movie m
        JOIN Show s ON s.movie = m
        JOIN s.screen sc
        JOIN sc.theatre t
        WHERE LOWER(t.city.cityName) = LOWER(:cityName)
          AND s.isActive = true
          AND m.isActive = true
        ORDER BY m.movieTitle
        """)
    List<Movie> findMoviesCurrentlyShowingInCity(@Param("cityName") String cityName);

    /**
     * Filter active movies in a city by optional genre and language.
     */
    @Query("""
        SELECT DISTINCT m FROM Movie m
        JOIN Show s ON s.movie = m
        JOIN s.screen sc
        JOIN sc.theatre t
        WHERE t.city.cityId = :cityId
          AND (:genre IS NULL OR LOWER(m.genre) = LOWER(:genre))
          AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
          AND s.isActive = true
          AND m.isActive = true
        ORDER BY m.movieTitle
        """)
    List<Movie> findMoviesWithFilters(
            @Param("cityId") Long cityId,
            @Param("genre") String genre,
            @Param("language") String language);
}
