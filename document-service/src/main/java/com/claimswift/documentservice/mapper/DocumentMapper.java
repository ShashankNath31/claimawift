package com.claimswift.documentservice.mapper;

import com.claimswift.documentservice.dto.DocumentResponse;
import com.claimswift.documentservice.dto.DocumentUploadRequest;
import com.claimswift.documentservice.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public Document toEntity(DocumentUploadRequest request, String fileName, String originalFileName,
                             String filePath, String mimeType, Long fileSize) {
        if (request == null) {
            return null;
        }

        Document document = new Document();
        document.setClaimId(request.getClaimId());
        document.setFileName(fileName);
        document.setOriginalFileName(originalFileName);
        document.setFilePath(filePath);
        document.setFileType(getFileExtension(originalFileName));
        document.setMimeType(mimeType);
        document.setFileSize(fileSize);
        document.setDocumentType(request.getDocumentType());
        document.setDescription(request.getDescription());

        return document;
    }

    public DocumentResponse toResponse(Document document) {
        if (document == null) {
            return null;
        }

        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setClaimId(document.getClaimId());
        response.setUploadedBy(document.getUploadedBy());
        response.setFileName(document.getFileName());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setFileType(document.getFileType());
        response.setMimeType(document.getMimeType());
        response.setFileSize(document.getFileSize());
        response.setDocumentType(document.getDocumentType());
        response.setDescription(document.getDescription());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        response.setDownloadUrl("/api/documents/" + document.getId() + "/download");

        return response;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}