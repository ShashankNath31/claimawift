package com.claimswift.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
