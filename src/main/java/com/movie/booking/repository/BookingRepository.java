package com.movie.booking.repository;

import com.movie.booking.entity.Booking;
import com.movie.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByCustomerUserIdOrderByCreatedAtDesc(Long customerUserId);

    List<Booking> findByShowShowIdAndBookingStatusIn(Long showId, List<BookingStatus> statuses);

    /**
     * Count seats already committed to a show — used to validate capacity
     * before accepting new bookings.
     */
    @Query("""
        SELECT COUNT(bs) FROM BookingSeat bs
        WHERE bs.booking.show.showId = :showId
          AND bs.booking.bookingStatus IN ('PENDING','CONFIRMED')
        """)
    int countCommittedSeatsForShow(@Param("showId") Long showId);
}
