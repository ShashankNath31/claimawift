package com.claimswift.claimservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimBankDetailsResponse {
    private Long claimId;
    private String beneficiaryName;
    private String accountNumber;
    private String ifscCode;
    private String bankName;
    private LocalDateTime updatedAt;
}
