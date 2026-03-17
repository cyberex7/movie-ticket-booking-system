package com.xyz.booking.service;

import com.xyz.booking.dto.response.PriceCalculationResponse;
import com.xyz.booking.entity.Seat;
import com.xyz.booking.entity.Show;
import com.xyz.booking.service.strategy.DiscountStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates all registered DiscountStrategy implementations.
 * All applicable strategies are stacked — both an afternoon discount and a
 * third-ticket discount can fire on the same booking simultaneously.
 *
 * Adding a new offer = adding a new @Component; this class never changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfferService {

    /** All @Component implementations of DiscountStrategy are auto-injected. */
    private final List<DiscountStrategy> discountStrategies;

    /**
     * Evaluates every registered strategy against the given show and seats,
     * stacks all applicable discounts, and returns a full pricing breakdown.
     *
     * @param show          the show being booked
     * @param selectedSeats the seats the customer selected
     * @return pricing breakdown with gross amount, total discount, net payable,
     *         and a detail entry for each offer that fired
     */
    public PriceCalculationResponse calculateFinalPrice(Show show, List<Seat> selectedSeats) {
        BigDecimal grossAmount = selectedSeats.stream()
                .map(Seat::getTicketPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PriceCalculationResponse.AppliedOfferDetail> appliedOffers = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (DiscountStrategy strategy : discountStrategies) {
            if (strategy.isApplicable(show, selectedSeats)) {
                BigDecimal discountAmount =
                        strategy.calculateDiscount(show, selectedSeats, grossAmount);

                if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Applied offer [{}]: ₹{}", strategy.getStrategyName(), discountAmount);

                    appliedOffers.add(PriceCalculationResponse.AppliedOfferDetail.builder()
                            .offerName(strategy.getStrategyName())
                            .offerCode(strategy.getStrategyName())
                            .discountType("PERCENTAGE")
                            .discountAmount(discountAmount)
                            .offerDescription(strategy.getStrategyDescription())
                            .build());

                    totalDiscount = totalDiscount.add(discountAmount);
                }
            }
        }

        BigDecimal netPayableAmount =
                grossAmount.subtract(totalDiscount).max(BigDecimal.ZERO);

        return PriceCalculationResponse.builder()
                .grossAmount(grossAmount)
                .totalDiscountAmount(totalDiscount)
                .netPayableAmount(netPayableAmount)
                .appliedOffers(appliedOffers)
                .build();
    }
}
