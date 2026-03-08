package com.claimswift.claimservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ClaimRequest {

    @NotBlank(message = "Policy number is required")
    private String policyNumber;

    @NotBlank(message = "Vehicle registration is required")
    private String vehicleRegistration;

    private String vehicleMake;
    private String vehicleModel;
    private Integer vehicleYear;

    @NotNull(message = "Incident date is required")
    private LocalDate incidentDate;

    private String incidentLocation;
    private String incidentDescription;

    @NotNull(message = "Claim amount is required")
    @Positive(message = "Claim amount must be positive")
    private BigDecimal claimAmount;
}