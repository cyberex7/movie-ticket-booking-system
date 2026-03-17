package com.movie.booking.service.strategy;

import com.movie.booking.entity.Seat;
import com.movie.booking.entity.Show;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.List;

/**
 * Applies a configurable percentage discount when the show falls within the
 * afternoon window (default 12:00–16:00, both boundaries configurable via
 * application.yml → app.offers.afternoon-*).
 *
 * The discount is applied to the full gross booking amount.
 */
@Component
public class AfternoonShowDiscountStrategy implements DiscountStrategy {

    @Value("${app.offers.afternoon-discount-percent:20}")
    private int afternoonDiscountPercent;

    @Value("${app.offers.afternoon-start-hour:12}")
    private int afternoonStartHour;

    @Value("${app.offers.afternoon-end-hour:16}")
    private int afternoonEndHour;

    @Override
    public boolean isApplicable(Show show, List<Seat> selectedSeats) {
        LocalTime showTime     = show.getShowTime();
        LocalTime windowStart  = LocalTime.of(afternoonStartHour, 0);
        LocalTime windowEnd    = LocalTime.of(afternoonEndHour, 0);
        return !showTime.isBefore(windowStart) && showTime.isBefore(windowEnd);
    }

    @Override
    public BigDecimal calculateDiscount(Show show, List<Seat> selectedSeats, BigDecimal grossAmount) {
        return grossAmount
                .multiply(BigDecimal.valueOf(afternoonDiscountPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return "AFTERNOON_SHOW_DISCOUNT";
    }

    @Override
    public String getStrategyDescription() {
        return afternoonDiscountPercent + "% off for shows between "
                + afternoonStartHour + ":00 and " + afternoonEndHour + ":00";
    }
}
