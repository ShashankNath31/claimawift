package com.claimswift.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginChallengeResponse {
    private String challengeId;
    private String maskedEmail;
    private Long expiresInSeconds;
}
