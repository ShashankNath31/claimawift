package com.claimswift.claimservice.dto;

import com.claimswift.claimservice.entity.Claim;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Claim Status Update Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimStatusUpdateRequest {
    
    @NotNull(message = "Status is required")
    private Claim.ClaimStatus status;
    
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    private Long assignedAdjusterId;

    private BigDecimal approvedAmount;
}
