package com.claimswift.assessmentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Adjustment Entity - Tracks all adjustments made to claim assessments
 */
@Entity
@Table(name = "adjustments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Adjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "previous_amount", precision = 15, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "adjusted_amount", precision = 15, scale = 2)
    private BigDecimal adjustedAmount;

    @Column(name = "difference_amount", precision = 15, scale = 2)
    private BigDecimal differenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private AdjustmentType adjustmentType;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "detailed_notes", length = 2000)
    private String detailedNotes;

    @Column(name = "adjusted_by", nullable = false)
    private Long adjustedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    public enum AdjustmentType {
        DEPRECIATION_APPLIED,
        EXCESS_DEDUCTED,
        PART_DAMAGE_ONLY,
        PRE_EXISTING_DAMAGE,
        BETTERMENT_APPLIED,
        POLICY_LIMIT_REACHED,
        DUPLICATE_CLAIM,
        INVESTIGATION_REQUIRED,
        DOCUMENTATION_INCOMPLETE,
        OTHER
    }
}