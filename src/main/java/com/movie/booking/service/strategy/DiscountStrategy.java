package com.movie.booking.service.strategy;

import com.movie.booking.entity.Seat;
import com.movie.booking.entity.Show;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy Pattern — each offer rule is a self-contained, independently
 * testable component. Adding a new discount type means adding a new
 * @Component class; no existing service code changes (Open/Closed Principle).
 */
public interface DiscountStrategy {

    /**
     * Guards whether this strategy applies to the given show and seat selection.
     * Called before calculateDiscount to avoid unnecessary computation.
     */
    boolean isApplicable(Show show, List<Seat> selectedSeats);

    /**
     * Computes the discount amount. Only called when isApplicable() is true.
     *
     * @param show          the show being booked
     * @param selectedSeats the seats the customer has chosen
     * @param grossAmount   total price before any discounts
     * @return discount to subtract (never negative)
     */
    BigDecimal calculateDiscount(Show show, List<Seat> selectedSeats, BigDecimal grossAmount);

    /** Short machine-readable identifier used in audit logs and receipts. */
    String getStrategyName();

    /** Human-readable description shown on the booking confirmation. */
    String getStrategyDescription();
}
