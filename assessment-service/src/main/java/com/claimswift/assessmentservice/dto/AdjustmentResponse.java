package com.claimswift.assessmentservice.dto;

import com.claimswift.assessmentservice.entity.Adjustment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdjustmentResponse {
    private Long id;
    private Long assessmentId;
    private Long claimId;
    private BigDecimal previousAmount;
    private BigDecimal adjustedAmount;
    private BigDecimal differenceAmount;
    private Adjustment.AdjustmentType adjustmentType;
    private String reason;
    private String detailedNotes;
    private Long adjustedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}