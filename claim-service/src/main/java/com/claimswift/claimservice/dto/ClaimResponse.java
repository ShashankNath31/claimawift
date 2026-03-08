package com.claimswift.claimservice.dto;

import com.claimswift.claimservice.entity.Claim;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Claim Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponse {
    private Long id;
    private String claimNumber;
    private String policyNumber;
    private Long policyholderId;
    private String policyholderName;
    private String policyholderEmail;
    private String policyholderPhone;
    
    // Vehicle Information
    private String vehicleRegistrationNumber;
    private String vehicleMake;
    private String vehicleModel;
    private Integer vehicleYear;
    private String vinNumber;
    
    // Claim Details
    private Claim.ClaimStatus status;
    private BigDecimal claimAmount;
    private BigDecimal approvedAmount;
    private String description;
    private LocalDate incidentDate;
    private String incidentLocation;
    private String incidentDescription;
    
    // Assessment
    private Long assignedAdjusterId;
    private String adjusterName;
    private String adjusterNotes;
    
    // Documents
    private Integer documentCount;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime paidAt;
}
