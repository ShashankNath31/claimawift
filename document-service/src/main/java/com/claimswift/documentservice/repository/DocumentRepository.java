package com.claimswift.documentservice.repository;

import com.claimswift.documentservice.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Document Repository
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByClaimId(Long claimId);

    List<Document> findByUploadedBy(Long uploadedBy);

    List<Document> findByClaimIdAndDocumentType(Long claimId, Document.DocumentType documentType);

    List<Document> findByDocumentType(Document.DocumentType documentType);

    long countByClaimId(Long claimId);
}
