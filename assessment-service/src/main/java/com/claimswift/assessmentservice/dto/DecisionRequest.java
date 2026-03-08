package com.claimswift.assessmentservice.dto;

import com.claimswift.assessmentservice.entity.Assessment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DecisionRequest {

    @NotNull(message = "Assessment ID is required")
    private Long assessmentId;

    @NotNull(message = "Decision is required")
    private Assessment.AssessmentDecision decision;

    @Positive(message = "Final amount must be greater than 0")
    private BigDecimal finalAmount;

    private String justification;
}
