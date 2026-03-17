package com.movie.booking.service.strategy;

import com.movie.booking.entity.Seat;
import com.movie.booking.entity.Show;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Applies a configurable % discount on every 3rd ticket in the booking.
 *
 * Seats are sorted by ticketPrice descending so the discount always targets
 * the highest-priced 3rd ticket, giving the customer maximum benefit.
 *
 * Example — 5 seats sorted desc [₹500, ₹300, ₹300, ₹200, ₹150]:
 *   Position 1: ₹500 — full price
 *   Position 2: ₹300 — full price
 *   Position 3: ₹300 → 50% off = ₹150 discount   ← every 3rd
 *   Position 4: ₹200 — full price
 *   Position 5: ₹150 — full price
 *   (Position 6 would also get 50% off if it existed)
 *   Total discount: ₹150
 */
@Component
public class ThirdTicketDiscountStrategy implements DiscountStrategy {

    @Value("${app.offers.third-ticket-discount-percent:50}")
    private int thirdTicketDiscountPercent;

    @Override
    public boolean isApplicable(Show show, List<Seat> selectedSeats) {
        return selectedSeats.size() >= 3;
    }

    @Override
    public BigDecimal calculateDiscount(Show show, List<Seat> selectedSeats, BigDecimal grossAmount) {
        List<Seat> sortedByPriceDesc = selectedSeats.stream()
                .sorted(Comparator.comparing(Seat::getTicketPrice).reversed())
                .toList();

        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (int index = 2; index < sortedByPriceDesc.size(); index += 3) {
            BigDecimal discountOnThisSeat = sortedByPriceDesc.get(index).getTicketPrice()
                    .multiply(BigDecimal.valueOf(thirdTicketDiscountPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalDiscount = totalDiscount.add(discountOnThisSeat);
        }
        return totalDiscount;
    }

    @Override
    public String getStrategyName() {
        return "THIRD_TICKET_DISCOUNT";
    }

    @Override
    public String getStrategyDescription() {
        return thirdTicketDiscountPercent + "% off on every 3rd ticket";
    }
}
