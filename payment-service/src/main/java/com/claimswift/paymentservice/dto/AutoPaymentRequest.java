package com.claimswift.paymentservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AutoPaymentRequest {

    @NotNull(message = "Claim ID is required")
    private Long claimId;

    @NotNull(message = "Policyholder ID is required")
    private Long policyholderId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String claimNumber;

    private Boolean forceSuccess = true;
}
