package com.claimswift.documentservice.service;

import com.claimswift.documentservice.client.ClaimServiceClient;
import com.claimswift.documentservice.dto.DocumentResponse;
import com.claimswift.documentservice.dto.DocumentUploadRequest;
import com.claimswift.documentservice.entity.Document;
import com.claimswift.documentservice.exception.InvalidFileException;
import com.claimswift.documentservice.exception.UnsupportedDocumentTypeException;
import com.claimswift.documentservice.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ClaimServiceClient claimServiceClient;

    @InjectMocks
    private DocumentService documentService;

    @TempDir
    Path tempDir;

    private DocumentUploadRequest uploadRequest;

    @BeforeEach
    void setUp() {
        uploadRequest = new DocumentUploadRequest();
        uploadRequest.setClaimId(100L);
        uploadRequest.setDocumentType(Document.DocumentType.CLAIM_FORM);
        uploadRequest.setDescription("test");

        ReflectionTestUtils.setField(documentService, "baseStoragePath", tempDir.toString());
        ReflectionTestUtils.setField(documentService, "maxFileSize", 1024L * 1024L);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadDocumentPersistsMetadataAndStoresFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "damage.pdf",
                "application/pdf",
                "%PDF-1.7 sample".getBytes()
        );
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        DocumentResponse response = documentService.uploadDocument(file, uploadRequest, 7L, "tester");

        assertNotNull(response);
        assertEquals(100L, response.getClaimId());
        assertEquals(Document.DocumentType.CLAIM_FORM, response.getDocumentType());
        verify(documentRepository).save(any(Document.class));

        Path claimDir = tempDir.resolve("100");
        assertEquals(1, Files.list(claimDir).count());
    }

    @Test
    void uploadDocumentRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "damage.pdf", "application/pdf", new byte[0]);
        assertThrows(InvalidFileException.class, () -> documentService.uploadDocument(file, uploadRequest, 1L, "u"));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void uploadDocumentRejectsNonPdfExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "damage.txt",
                "application/pdf",
                "%PDF-1.7 sample".getBytes()
        );
        assertThrows(UnsupportedDocumentTypeException.class, () -> documentService.uploadDocument(file, uploadRequest, 1L, "u"));
    }

    @Test
    void uploadDocumentRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "damage.pdf",
                "image/png",
                "%PDF-1.7 sample".getBytes()
        );
        assertThrows(UnsupportedDocumentTypeException.class, () -> documentService.uploadDocument(file, uploadRequest, 1L, "u"));
    }

    @Test
    void uploadDocumentRejectsInvalidPdfSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "damage.pdf",
                "application/pdf",
                "NOTPDF".getBytes()
        );
        assertThrows(UnsupportedDocumentTypeException.class, () -> documentService.uploadDocument(file, uploadRequest, 1L, "u"));
    }

    @Test
    void getDocumentAndFindByClaimAndType() {
        Document document = baseDocument();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.findByClaimId(100L)).thenReturn(List.of(document));
        when(documentRepository.findByClaimIdAndDocumentType(100L, Document.DocumentType.CLAIM_FORM))
                .thenReturn(List.of(document));
        when(documentRepository.findByUploadedBy(7L)).thenReturn(List.of(document));
        when(documentRepository.countByClaimId(100L)).thenReturn(3L);

        DocumentResponse byId = documentService.getDocument(1L);
        List<DocumentResponse> byClaim = documentService.getDocumentsByClaim(100L);
        List<DocumentResponse> byType = documentService.getDocumentsByType(100L, Document.DocumentType.CLAIM_FORM);
        List<DocumentResponse> byUser = documentService.getDocumentsByUser(7L);
        long count = documentService.countDocumentsByClaim(100L);

        assertEquals(1L, byId.getId());
        assertEquals(1, byClaim.size());
        assertEquals(1, byType.size());
        assertEquals(1, byUser.size());
        assertEquals(3L, count);
    }

    @Test
    void deleteDocumentRemovesFileAndRecord() throws Exception {
        Path claimDir = Files.createDirectories(tempDir.resolve("100"));
        Path filePath = claimDir.resolve("test.pdf");
        Files.write(filePath, "%PDF-1.7".getBytes());

        Document document = baseDocument();
        document.setFilePath(filePath.toString());

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.deleteDocument(1L, "tester");

        verify(documentRepository).delete(document);
    }

    @Test
    void downloadDocumentThrowsWhenFileMissing() {
        Document document = baseDocument();
        document.setFilePath(tempDir.resolve("missing.pdf").toString());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(RuntimeException.class, () -> documentService.downloadDocument(1L));
    }

    @Test
    void documentPathAndResourceLoading() throws Exception {
        Path filePath = tempDir.resolve("ok.pdf");
        Files.write(filePath, "%PDF-1.7".getBytes());
        Document document = baseDocument();
        document.setFilePath(filePath.toString());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        Path resolved = documentService.getDocumentFilePath(1L);
        Resource resource = documentService.loadFileAsResource(resolved);

        assertEquals(filePath, resolved);
        assertNotNull(resource);
        assertThrows(RuntimeException.class, () -> documentService.loadFileAsResource(tempDir.resolve("no-file.pdf")));
    }

    @Test
    void policyholderUserCannotReadOtherUsersDocuments() {
        setPolicyholderOnlyAuth();
        assertThrows(AccessDeniedException.class, () -> documentService.getDocumentsByUser(2L, 1L));
    }

    @Test
    void policyholderClaimAccessIsValidatedAgainstClaimService() {
        setPolicyholderOnlyAuth();
        Document document = baseDocument();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(claimServiceClient.getClaimById(100L)).thenReturn(Map.of("data", Map.of("policyholderId", 99L)));

        assertThrows(AccessDeniedException.class, () -> documentService.getDocument(1L, 7L));
    }

    private Document baseDocument() {
        return Document.builder()
                .id(1L)
                .claimId(100L)
                .uploadedBy(7L)
                .fileName("doc.pdf")
                .filePath(tempDir.resolve("100").resolve("doc.pdf").toString())
                .fileType("PDF")
                .fileSize(120L)
                .documentType(Document.DocumentType.CLAIM_FORM)
                .description("desc")
                .originalFileName("doc.pdf")
                .mimeType("application/pdf")
                .build();
    }

    private void setPolicyholderOnlyAuth() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "policyholder",
                "n/a",
                Set.of(new SimpleGrantedAuthority("ROLE_POLICYHOLDER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
