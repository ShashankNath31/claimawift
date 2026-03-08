package com.claimswift.paymentservice.dto;

import com.claimswift.paymentservice.entity.PaymentTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    @NotNull(message = "Claim ID is required")
    private Long claimId;

    private String claimNumber;

    @NotNull(message = "Policyholder ID is required")
    private Long policyholderId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentTransaction.PaymentMethod paymentMethod;

    @NotBlank(message = "Beneficiary name is required")
    private String beneficiaryName;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotBlank(message = "IFSC code is required")
    private String ifscCode;

    private String bankName;

    private Boolean forceSimulateSuccess = false;

    private Boolean forceSimulateFailure = false;

    private String simulateFailureReason;
}
