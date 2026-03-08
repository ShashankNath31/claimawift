package com.claimswift.claimservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyPortfolioResponse {
    private Long totalPolicies;
    private Long activePolicies;
    private Long expiredPolicies;
    private List<PolicyResponse> policies;
}
