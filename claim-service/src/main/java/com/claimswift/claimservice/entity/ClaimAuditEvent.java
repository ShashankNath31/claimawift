package com.claimswift.claimservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "claim_audit_events",
        indexes = {
                @Index(name = "idx_claim_audit_claim_id", columnList = "claim_id"),
                @Index(name = "idx_claim_audit_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "claim_number", nullable = false, length = 50)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AuditAction actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private Claim.ClaimStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 30)
    private Claim.ClaimStatus newStatus;

    @Column(name = "assigned_adjuster_id")
    private Long assignedAdjusterId;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "changed_by", nullable = false, length = 120)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AuditAction {
        CLAIM_SUBMITTED,
        CLAIM_UPDATED,
        CLAIM_STATUS_UPDATED,
        CLAIM_ASSIGNED,
        CLAIM_UNASSIGNED
    }
}
