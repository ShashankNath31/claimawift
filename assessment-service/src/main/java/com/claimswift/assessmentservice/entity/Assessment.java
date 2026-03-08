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
 * Assessment Entity - Stores claim assessment details
 */
@Entity
@Table(name = "assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private Long claimId;

    @Column(name = "assessor_id", nullable = false)
    private Long assessorId;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private AssessmentDecision decision;

    @Column(name = "assessed_amount", precision = 15, scale = 2)
    private BigDecimal assessedAmount;

    @Column(name = "recommended_amount", precision = 15, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "justification", length = 2000)
    private String justification;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 30)
    private ValidationStatus validationStatus;

    @Column(name = "rule_violations", length = 1000)
    private String ruleViolations;

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

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AssessmentDecision {
        APPROVED, REJECTED, ADJUSTED, PENDING_REVIEW
    }

    public enum ValidationStatus {
        VALID, INVALID, REQUIRES_DOCUMENTS, REQUIRES_INSPECTION
    }
}