package com.claimswift.assessmentservice.dto;

import com.claimswift.assessmentservice.entity.Adjustment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustmentRequest {

    @NotNull(message = "Assessment ID is required")
    private Long assessmentId;

    @NotNull(message = "Claim ID is required")
    private Long claimId;

    @NotNull(message = "Adjusted amount is required")
    @Positive(message = "Adjusted amount must be greater than 0")
    private BigDecimal adjustedAmount;

    @NotNull(message = "Adjustment type is required")
    private Adjustment.AdjustmentType adjustmentType;

    @NotBlank(message = "Reason is required")
    private String reason;

    private String detailedNotes;
}
