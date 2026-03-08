package com.claimswift.claimservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Claim Entity - Represents an insurance claim
 */
@Entity
@Table(
        name = "claims",
        indexes = {
                @Index(name = "idx_claim_claim_number", columnList = "claim_number"),
                @Index(name = "idx_claim_policy_number", columnList = "policy_number"),
                @Index(name = "idx_claim_status", columnList = "status"),
                @Index(name = "idx_claim_incident_date", columnList = "incident_date"),
                @Index(name = "idx_claim_claimant_name", columnList = "claimant_name"),
                @Index(name = "idx_claim_claimant_phone", columnList = "claimant_phone"),
                @Index(name = "idx_claim_claimant_status_date", columnList = "claimant_id,status,incident_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 50)
    private String claimNumber;

    @Column(name = "policy_number", nullable = false, length = 50)
    private String policyNumber;

    @Column(name = "claimant_id", nullable = false)
    private Long claimantId;

    @Column(name = "claimant_name", length = 120)
    private String claimantName;

    @Column(name = "claimant_phone", length = 20)
    private String claimantPhone;

    @Column(name = "vehicle_registration", nullable = false, length = 20)
    private String vehicleRegistration;

    @Column(name = "vehicle_make", length = 50)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 50)
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", length = 255)
    private String incidentLocation;

    @Column(name = "incident_description", columnDefinition = "TEXT")
    private String incidentDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClaimStatus status;

    @Column(name = "claim_amount", precision = 15, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "beneficiary_name", length = 120)
    private String beneficiaryName;

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_details_updated_at")
    private LocalDateTime bankDetailsUpdatedAt;

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "adjuster_id")
    private Long adjusterId;

    // Audit Fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    public enum ClaimStatus {
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        ADJUSTED,
        PAID,
        PAYMENT_FAILED,
        CANCELLED
    }

    /**
     * Update status and track when it was changed
     */
    public void updateStatus(ClaimStatus newStatus, String updatedBy) {
        this.status = newStatus;
        this.statusChangedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }
}
