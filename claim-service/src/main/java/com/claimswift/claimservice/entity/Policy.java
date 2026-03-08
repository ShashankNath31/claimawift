package com.claimswift.claimservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "policies",
        indexes = {
                @Index(name = "idx_policy_number", columnList = "policy_number"),
                @Index(name = "idx_policy_holder", columnList = "policyholder_id"),
                @Index(name = "idx_policy_expiry", columnList = "expiry_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 50)
    private String policyNumber;

    @Column(name = "policyholder_id", nullable = false)
    private Long policyholderId;

    @Column(name = "policy_type", nullable = false, length = 80)
    private String policyType;

    @Column(name = "plan_name", length = 120)
    private String planName;

    @Column(name = "insurer_name", length = 120)
    private String insurerName;

    @Column(name = "vehicle_registration", nullable = false, length = 30)
    private String vehicleRegistration;

    @Column(name = "coverage_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal coverageAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
