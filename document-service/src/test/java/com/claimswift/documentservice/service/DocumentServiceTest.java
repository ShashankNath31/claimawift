package com.claimswift.documentservice.service;

import com.claimswift.documentservice.dto.DocumentUploadRequest;
import com.claimswift.documentservice.dto.DocumentResponse;
import com.claimswift.documentservice.entity.Document;
import com.claimswift.documentservice.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    private Document document;
    private DocumentUploadRequest documentRequest;
    private final Long userId = 1L;
    private final String username = "testuser";
    private final Long claimId = 100L;

    @BeforeEach
    void setUp() {
        document = new Document();
        document.setId(1L);
        document.setClaimId(claimId);
        document.setUploadedBy(userId);
        document.setFileName("test-file.pdf");
        document.setFilePath("/uploads/100/test-file.pdf");
        document.setFileType("PDF");
        document.setFileSize(1024L);
        document.setDocumentType(Document.DocumentType.CLAIM_FORM);
        document.setOriginalFileName("original.pdf");
        document.setMimeType("application/pdf");

        documentRequest = new DocumentUploadRequest();
        documentRequest.setClaimId(claimId);
        documentRequest.setDocumentType(Document.DocumentType.CLAIM_FORM);
        documentRequest.setDescription("Test document");
    }

    @Test
    void getDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        DocumentResponse response = documentService.getDocument(1L);

        assertNotNull(response);
        assertEquals(document.getId(), response.getId());
        assertEquals(document.getFileName(), response.getFileName());
    }

    @Test
    void getDocument_NotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> documentService.getDocument(99L));
    }

    @Test
    void getDocumentsByClaim_Success() {
        List<Document> documents = Arrays.asList(document);
        when(documentRepository.findByClaimId(claimId)).thenReturn(documents);

        List<DocumentResponse> responses = documentService.getDocumentsByClaim(claimId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    void getDocumentsByUser_Success() {
        List<Document> documents = Arrays.asList(document);
        when(documentRepository.findByUploadedBy(userId)).thenReturn(documents);

        List<DocumentResponse> responses = documentService.getDocumentsByUser(userId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    void getDocumentsByType_Success() {
        List<Document> documents = Arrays.asList(document);
        when(documentRepository.findByClaimIdAndDocumentType(claimId, Document.DocumentType.CLAIM_FORM))
            .thenReturn(documents);

        List<DocumentResponse> responses = documentService.getDocumentsByType(claimId, Document.DocumentType.CLAIM_FORM);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    void deleteDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.deleteDocument(1L, username);

        verify(documentRepository, times(1)).delete(any(Document.class));
    }
}