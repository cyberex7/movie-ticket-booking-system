package com.movie.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCancelRequest {

    @NotEmpty(message = "At least one booking reference is required")
    private List<String> bookingReferences;

    private String cancellationReason;
}
