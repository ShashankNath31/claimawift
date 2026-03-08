package com.claimswift.reportingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Claim Summary Report DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSummaryReport {
    private LocalDateTime reportGeneratedAt;
    private String reportPeriod;
    
    // Claim counts
    private Long totalClaims;
    private Long submittedClaims;
    private Long underReviewClaims;
    private Long approvedClaims;
    private Long rejectedClaims;
    private Long paidClaims;
    
    // Financial summary
    private BigDecimal totalClaimAmount;
    private BigDecimal totalApprovedAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal averageClaimAmount;
    
    // Status breakdown
    private Map<String, Long> claimsByStatus;
    
    // Trends
    private Long claimsThisMonth;
    private Long claimsLastMonth;
    private BigDecimal amountThisMonth;
    private BigDecimal amountLastMonth;
}