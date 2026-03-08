package com.claimswift.documentservice.controller;

import com.claimswift.documentservice.dto.DocumentResponse;
import com.claimswift.documentservice.dto.DocumentUploadRequest;
import com.claimswift.documentservice.entity.Document;
import com.claimswift.documentservice.service.DocumentService;
import com.claimswift.documentservice.util.StandardResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Document Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@Validated
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADMIN')")
    public ResponseEntity<StandardResponse<DocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("claimId") @Positive(message = "Claim ID must be positive") Long claimId,
            @RequestParam(value = "documentType", required = false) Document.DocumentType documentType,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long uploadedBy,
            @RequestAttribute("username") String username) {

        log.info("Document upload request for claim: {} by user: {}", claimId, username);

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setClaimId(claimId);
        request.setDocumentType(documentType);
        request.setDescription(description);

        DocumentResponse response = documentService.uploadDocument(file, request, uploadedBy, username);
        return ResponseEntity.ok(StandardResponse.success("Document uploaded successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<DocumentResponse>> getDocument(
            @PathVariable("id") @Positive(message = "Document ID must be positive") Long id,
            @RequestAttribute("userId") Long requesterUserId) {
        log.info("Get document request for id: {}", id);
        DocumentResponse response = documentService.getDocument(id, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success(response));
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<DocumentResponse>>> getDocumentsByClaim(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId,
            @RequestAttribute("userId") Long requesterUserId) {
        log.info("Get documents for claim: {}", claimId);
        List<DocumentResponse> documents = documentService.getDocumentsByClaim(claimId, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success(documents));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<DocumentResponse>>> getDocumentsByUser(
            @PathVariable("userId") @Positive(message = "User ID must be positive") Long userId,
            @RequestAttribute("userId") Long requesterUserId) {
        log.info("Get documents for user: {}", userId);
        List<DocumentResponse> documents = documentService.getDocumentsByUser(userId, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success(documents));
    }

    @GetMapping("/claim/{claimId}/type/{documentType}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<DocumentResponse>>> getDocumentsByType(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId,
            @PathVariable("documentType") Document.DocumentType documentType,
            @RequestAttribute("userId") Long requesterUserId) {
        log.info("Get documents for claim: {} and type: {}", claimId, documentType);
        List<DocumentResponse> documents = documentService.getDocumentsByType(claimId, documentType, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success(documents));
    }

    @GetMapping("/internal/claim/{claimId}/count")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getClaimDocumentCount(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId) {
        long count = documentService.countDocumentsByClaim(claimId);
        return ResponseEntity.ok(Map.of("claimId", claimId, "documentCount", count));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<Void>> deleteDocument(
            @PathVariable("id") @Positive(message = "Document ID must be positive") Long id,
            @RequestAttribute("userId") Long requesterUserId,
            @RequestAttribute("username") String username) {
        log.info("Delete document request for id: {} by user: {}", id, username);
        documentService.deleteDocument(id, requesterUserId, username);
        return ResponseEntity.ok(StandardResponse.success("Document deleted successfully", null));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable("id") @Positive(message = "Document ID must be positive") Long id,
            @RequestAttribute("userId") Long requesterUserId) throws IOException {
        log.info("Download document request for id: {}", id);

        Path filePath = documentService.getDocumentFilePath(id, requesterUserId);
        Resource resource = documentService.loadFileAsResource(filePath);

        DocumentResponse document = documentService.getDocument(id, requesterUserId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getOriginalFileName() + "\"")
                .body(resource);
    }
}
