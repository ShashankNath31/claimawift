package com.claimswift.documentservice.dto;

import com.claimswift.documentservice.entity.Document;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Document Upload Request DTO
 */
@Data
public class DocumentUploadRequest {

    @NotNull(message = "Claim ID is required")
    @Positive(message = "Claim ID must be positive")
    private Long claimId;

    private Document.DocumentType documentType;

    private String description;
}
