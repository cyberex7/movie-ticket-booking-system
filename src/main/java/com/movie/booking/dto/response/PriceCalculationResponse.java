package com.movie.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read Scenario 2 response: breakdown of pricing after all applicable
 * discount strategies have been evaluated and stacked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationResponse {

    private BigDecimal grossAmount;
    private BigDecimal totalDiscountAmount;
    private BigDecimal netPayableAmount;

    /** One entry per discount strategy that fired. */
    private List<AppliedOfferDetail> appliedOffers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedOfferDetail {
        private String offerName;
        private String offerCode;
        private String discountType;
        private BigDecimal discountAmount;
        private String offerDescription;
    }
}
