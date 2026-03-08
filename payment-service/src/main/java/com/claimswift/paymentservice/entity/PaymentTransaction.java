package com.claimswift.paymentservice.entity;

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
 * Payment Transaction Entity - Stores simulated payment transactions
 * 
 * IMPORTANT: This is a SIMULATED Payment Gateway (Sandbox Environment)
 * No real money is processed. All transactions are simulated for testing.
 */
@Entity
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", unique = true, nullable = false, length = 100)
    private String transactionId;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "policyholder_id", nullable = false)
    private Long policyholderId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "beneficiary_name", length = 200)
    private String beneficiaryName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "simulation_mode", nullable = false)
    @Builder.Default
    private Boolean simulationMode = true;

    @Column(name = "simulation_result", length = 50)
    private String simulationResult;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "processed_by")
    private Long processedBy;

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

    /**
     * Payment Status - Simulated
     */
    public enum PaymentStatus {
        INITIATED,
        PROCESSING,
        APPROVED,
        REJECTED,
        PENDING_VERIFICATION,
        FAILED,
        REFUNDED,
        CANCELLED
    }

    /**
     * Payment Method
     */
    public enum PaymentMethod {
        BANK_TRANSFER,
        CHEQUE,
        UPI,
        NEFT,
        RTGS,
        IMPS
    }
}
