package com.claimswift.documentservice.dto;

import com.claimswift.documentservice.entity.Document;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Document Response DTO
 */
@Data
public class DocumentResponse {

    private Long id;
    private Long claimId;
    private Long uploadedBy;
    private String fileName;
    private String originalFileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private Document.DocumentType documentType;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String downloadUrl;
}
