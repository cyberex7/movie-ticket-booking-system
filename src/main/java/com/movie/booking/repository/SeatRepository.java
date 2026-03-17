package com.movie.booking.repository;

import com.movie.booking.entity.Seat;
import com.movie.booking.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenScreenId(Long screenId);

    List<Seat> findByScreenScreenIdAndSeatStatus(Long screenId, SeatStatus seatStatus);

    /**
     * Returns seats on this screen that have not been booked or reserved
     * for the given show — used to build the available-seat grid for customers.
     */
    @Query("""
        SELECT s FROM Seat s
        WHERE s.screen.screenId = :screenId
          AND s.seatStatus = 'AVAILABLE'
          AND s.seatId NOT IN (
              SELECT bs.seat.seatId FROM BookingSeat bs
              WHERE bs.booking.show.showId = :showId
                AND bs.booking.bookingStatus IN ('PENDING','CONFIRMED')
          )
        ORDER BY s.rowLabel, s.columnNumber
        """)
    List<Seat> findAvailableSeatsForShow(
            @Param("screenId") Long screenId,
            @Param("showId") Long showId);

    /**
     * Acquires a pessimistic write lock on the requested seats before any
     * status check, preventing two concurrent transactions from booking
     * the same seat simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.seatId IN :seatIds")
    List<Seat> findSeatsByIdWithPessimisticLock(@Param("seatIds") List<Long> seatIds);

    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = :newStatus WHERE s.seatId IN :seatIds")
    int bulkUpdateSeatStatus(
            @Param("seatIds") List<Long> seatIds,
            @Param("newStatus") SeatStatus newStatus);
}
