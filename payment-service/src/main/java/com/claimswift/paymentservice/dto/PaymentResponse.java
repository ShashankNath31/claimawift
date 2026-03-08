package com.claimswift.paymentservice.dto;

import com.claimswift.paymentservice.entity.PaymentTransaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponse {
    private Long id;
    private String transactionId;
    private Long claimId;
    private Long policyholderId;
    private BigDecimal amount;
    private PaymentTransaction.PaymentStatus status;
    private PaymentTransaction.PaymentMethod paymentMethod;
    private String beneficiaryName;
    private String referenceNumber;
    private Boolean simulationMode;
    private String simulationResult;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}