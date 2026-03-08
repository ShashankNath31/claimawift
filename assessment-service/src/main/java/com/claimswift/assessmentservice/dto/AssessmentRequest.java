package com.claimswift.assessmentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssessmentRequest {

    @NotNull(message = "Claim ID is required")
    private Long claimId;

    @Positive(message = "Assessed amount must be greater than 0")
    private BigDecimal assessedAmount;

    private String justification;

    private String notes;
}
