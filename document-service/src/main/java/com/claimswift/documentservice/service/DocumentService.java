package com.claimswift.documentservice.service;

import com.claimswift.documentservice.dto.DocumentResponse;
import com.claimswift.documentservice.dto.DocumentUploadRequest;
import com.claimswift.documentservice.client.ClaimServiceClient;
import com.claimswift.documentservice.entity.Document;
import com.claimswift.documentservice.exception.InvalidFileException;
import com.claimswift.documentservice.exception.UnsupportedDocumentTypeException;
import com.claimswift.documentservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ClaimServiceClient claimServiceClient;

    @Value("${file.storage.path:/uploads}")
    private String baseStoragePath;

    @Value("${file.max-size:10485760}")
    private Long maxFileSize;

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, DocumentUploadRequest request, Long uploadedBy, String username) {
        ensurePolicyholderClaimAccess(request.getClaimId(), uploadedBy);
        validateFile(file);

        try {
            Path claimUploadPath = Paths.get(baseStoragePath, String.valueOf(request.getClaimId()));
            if (!Files.exists(claimUploadPath)) {
                Files.createDirectories(claimUploadPath);
                log.info("Created directory: {}", claimUploadPath);
            }

            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = UUID.randomUUID() + "." + fileExtension;

            Path filePath = claimUploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Document document = Document.builder()
                    .claimId(request.getClaimId())
                    .uploadedBy(uploadedBy)
                    .fileName(uniqueFileName)
                    .filePath(filePath.toString())
                    .fileType(fileExtension.toUpperCase())
                    .fileSize(file.getSize())
                    .documentType(request.getDocumentType())
                    .description(request.getDescription())
                    .originalFileName(originalFileName)
                    .mimeType(file.getContentType())
                    .createdBy(username)
                    .updatedBy(username)
                    .build();

            Document savedDocument = documentRepository.save(document);
            return mapToResponse(savedDocument);
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            throw new RuntimeException("Failed to upload document: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        return getDocument(id, null);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id, Long requesterUserId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        ensurePolicyholderClaimAccess(document.getClaimId(), requesterUserId);
        return mapToResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByClaim(Long claimId) {
        return getDocumentsByClaim(claimId, null);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByClaim(Long claimId, Long requesterUserId) {
        ensurePolicyholderClaimAccess(claimId, requesterUserId);
        return documentRepository.findByClaimId(claimId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByUser(Long userId) {
        return getDocumentsByUser(userId, null);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByUser(Long userId, Long requesterUserId) {
        if (isPolicyholderOnly() && (requesterUserId == null || !requesterUserId.equals(userId))) {
            throw new AccessDeniedException("You are not allowed to access documents for this user.");
        }
        return documentRepository.findByUploadedBy(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByType(Long claimId, Document.DocumentType documentType) {
        return getDocumentsByType(claimId, documentType, null);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByType(Long claimId, Document.DocumentType documentType, Long requesterUserId) {
        ensurePolicyholderClaimAccess(claimId, requesterUserId);
        return documentRepository.findByClaimIdAndDocumentType(claimId, documentType).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countDocumentsByClaim(Long claimId) {
        return documentRepository.countByClaimId(claimId);
    }

    @Transactional
    public void deleteDocument(Long id, String username) {
        deleteDocument(id, null, username);
    }

    @Transactional
    public void deleteDocument(Long id, Long requesterUserId, String username) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        ensurePolicyholderClaimAccess(document.getClaimId(), requesterUserId);

        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);

            Path claimDir = filePath.getParent();
            if (claimDir != null && Files.exists(claimDir) && Files.list(claimDir).count() == 0) {
                Files.deleteIfExists(claimDir);
            }

            documentRepository.delete(document);
            log.info("Document deleted: {} by {}", id, username);
        } catch (IOException e) {
            log.error("Failed to delete document file", e);
            throw new RuntimeException("Failed to delete document: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));

        try {
            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found on disk: " + document.getFilePath());
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read document file", e);
            throw new RuntimeException("Failed to download document: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Path getDocumentFilePath(Long id) {
        return getDocumentFilePath(id, null);
    }

    @Transactional(readOnly = true)
    public Path getDocumentFilePath(Long id, Long requesterUserId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        ensurePolicyholderClaimAccess(document.getClaimId(), requesterUserId);
        return Paths.get(document.getFilePath());
    }

    @Transactional(readOnly = true)
    public Resource loadFileAsResource(Path filePath) {
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not found or not readable: " + filePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path: " + filePath, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new InvalidFileException("File size exceeds maximum allowed size");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName).toLowerCase(Locale.ROOT);
        if (!"pdf".equals(extension)) {
            throw new UnsupportedDocumentTypeException("Only PDF documents are allowed. Upload a .pdf file.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            List<String> allowedTypes = List.of(
                    "application/pdf",
                    "application/x-pdf",
                    "application/octet-stream"
            );
            String normalizedType = contentType.toLowerCase(Locale.ROOT);
            if (!allowedTypes.contains(normalizedType)) {
                throw new UnsupportedDocumentTypeException(
                        "Unsupported file type '" + contentType + "'. Only PDF is allowed.");
            }
        }

        if (!hasPdfSignature(file)) {
            throw new UnsupportedDocumentTypeException("Uploaded file content is not a valid PDF document.");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean hasPdfSignature(MultipartFile file) {
        try {
            byte[] header = file.getInputStream().readNBytes(5);
            return header.length == 5
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F'
                    && header[4] == '-';
        } catch (IOException ex) {
            throw new InvalidFileException("Unable to read uploaded file for validation");
        }
    }

    private DocumentResponse mapToResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setClaimId(document.getClaimId());
        response.setUploadedBy(document.getUploadedBy());
        response.setFileName(document.getFileName());
        response.setFileType(document.getFileType());
        response.setFileSize(document.getFileSize());
        response.setDocumentType(document.getDocumentType());
        response.setDescription(document.getDescription());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setMimeType(document.getMimeType());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        response.setDownloadUrl("/api/documents/" + document.getId() + "/download");
        return response;
    }

    private void ensurePolicyholderClaimAccess(Long claimId, Long requesterUserId) {
        if (!isPolicyholderOnly()) {
            return;
        }
        if (requesterUserId == null) {
            throw new AccessDeniedException("Unable to validate claim access.");
        }
        Map<String, Object> claimEnvelope = claimServiceClient.getClaimById(claimId);
        Object data = claimEnvelope.get("data");
        if (!(data instanceof Map<?, ?> claimMap)) {
            throw new AccessDeniedException("Unable to validate claim access.");
        }
        Long ownerId = parseLong(claimMap.get("policyholderId"));
        if (ownerId == null || !ownerId.equals(requesterUserId)) {
            throw new AccessDeniedException("You are not allowed to access documents for this claim.");
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isPolicyholderOnly() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.contains("ROLE_POLICYHOLDER")
                && !roles.contains("ROLE_ADJUSTER")
                && !roles.contains("ROLE_MANAGER")
                && !roles.contains("ROLE_ADMIN");
    }
}
