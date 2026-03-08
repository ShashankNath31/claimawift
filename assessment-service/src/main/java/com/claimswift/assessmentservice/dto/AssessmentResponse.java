package com.claimswift.assessmentservice.dto;

import com.claimswift.assessmentservice.entity.Assessment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssessmentResponse {
    private Long id;
    private Long claimId;
    private Long assessorId;
    private BigDecimal riskScore;
    private Assessment.RiskLevel riskLevel;
    private Assessment.AssessmentDecision decision;
    private BigDecimal assessedAmount;
    private BigDecimal recommendedAmount;
    private String justification;
    private String notes;
    private Assessment.ValidationStatus validationStatus;
    private String ruleViolations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AdjustmentResponse> adjustments;
}