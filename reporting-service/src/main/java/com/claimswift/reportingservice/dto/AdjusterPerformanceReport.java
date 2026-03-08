package com.claimswift.reportingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Adjuster Performance Report DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjusterPerformanceReport {
    private LocalDateTime reportGeneratedAt;
    private String reportPeriod;
    
    // Overall summary
    private Long totalAdjusters;
    private Long totalClaimsProcessed;
    private BigDecimal totalAmountProcessed;
    private Double averageProcessingTime;
    
    // Individual adjuster performance
    private List<AdjusterPerformance> adjusterPerformances;
    
    /**
     * Individual adjuster performance details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjusterPerformance {
        private Long adjusterId;
        private String adjusterName;
        private String adjusterEmail;
        
        // Claims metrics
        private Long totalClaimsAssigned;
        private Long claimsApproved;
        private Long claimsRejected;
        private Long claimsPending;
        
        // Financial metrics
        private BigDecimal totalClaimAmount;
        private BigDecimal approvedAmount;
        private BigDecimal rejectedAmount;
        
        // Performance metrics
        private Double averageProcessingTimeDays;
        private Double approvalRate;
        private Long totalDecisions;
    }
}