package com.claimswift.claimservice.dto;

import com.claimswift.claimservice.entity.Claim;
import com.claimswift.claimservice.entity.ClaimAuditEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimAuditEventResponse {
    private Long id;
    private Long claimId;
    private String claimNumber;
    private ClaimAuditEvent.AuditAction actionType;
    private Claim.ClaimStatus oldStatus;
    private Claim.ClaimStatus newStatus;
    private Long assignedAdjusterId;
    private BigDecimal approvedAmount;
    private String details;
    private String changedBy;
    private LocalDateTime createdAt;
}
