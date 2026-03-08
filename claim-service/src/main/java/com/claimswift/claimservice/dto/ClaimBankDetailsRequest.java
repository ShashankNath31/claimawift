package com.claimswift.claimservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimBankDetailsRequest {

    @NotBlank(message = "Beneficiary name is required")
    @Size(max = 120, message = "Beneficiary name must be at most 120 characters")
    private String beneficiaryName;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
    private String accountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "IFSC code format is invalid")
    private String ifscCode;

    @NotBlank(message = "Bank name is required")
    @Size(max = 120, message = "Bank name must be at most 120 characters")
    private String bankName;
}
