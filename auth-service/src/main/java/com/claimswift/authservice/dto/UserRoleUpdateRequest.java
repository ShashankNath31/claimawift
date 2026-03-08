package com.claimswift.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UserRoleUpdateRequest {

    @NotEmpty(message = "At least one role is required")
    private Set<String> roles;
}
