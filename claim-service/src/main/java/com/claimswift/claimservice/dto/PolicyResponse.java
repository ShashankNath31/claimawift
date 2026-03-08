package com.claimswift.claimservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResponse {
    private Long id;
    private String policyNumber;
    private String policyType;
    private String planName;
    private String insurerName;
    private String vehicleRegistration;
    private BigDecimal coverageAmount;
    private BigDecimal claimedAmount;
    private BigDecimal availableCoverage;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private String status;
}
