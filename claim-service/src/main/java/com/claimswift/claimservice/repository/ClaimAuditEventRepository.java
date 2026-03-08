package com.claimswift.claimservice.repository;

import com.claimswift.claimservice.entity.ClaimAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimAuditEventRepository extends JpaRepository<ClaimAuditEvent, Long> {
    List<ClaimAuditEvent> findByClaimIdOrderByCreatedAtDesc(Long claimId);
}
