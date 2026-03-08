package com.claimswift.assessmentservice.repository;

import com.claimswift.assessmentservice.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Optional<Assessment> findByClaimId(Long claimId);

    List<Assessment> findByAssessorId(Long assessorId);

    List<Assessment> findByDecision(Assessment.AssessmentDecision decision);

    List<Assessment> findByRiskLevel(Assessment.RiskLevel riskLevel);

    boolean existsByClaimId(Long claimId);

    void deleteByClaimId(Long claimId);
}