package com.claimswift.reportingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment Report DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReport {
    private LocalDateTime reportGeneratedAt;
    private String reportPeriod;
    
    // Payment counts
    private Long totalPayments;
    private Long approvedPayments;
    private Long rejectedPayments;
    private Long pendingPayments;
    
    // Financial summary
    private BigDecimal totalAmount;
    private BigDecimal approvedAmount;
    private BigDecimal rejectedAmount;
    private BigDecimal averagePaymentAmount;
    
    // Status breakdown
    private Map<String, Long> paymentsByStatus;
    private Map<String, BigDecimal> amountByStatus;
    
    // Payment method breakdown
    private Map<String, Long> paymentsByMethod;
    
    // Trends
    private Long paymentsThisMonth;
    private Long paymentsLastMonth;
    private BigDecimal amountThisMonth;
    private BigDecimal amountLastMonth;
}