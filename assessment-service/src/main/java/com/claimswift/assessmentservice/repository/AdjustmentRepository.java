package com.claimswift.assessmentservice.repository;

import com.claimswift.assessmentservice.entity.Adjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

    List<Adjustment> findByAssessmentId(Long assessmentId);

    List<Adjustment> findByClaimId(Long claimId);

    List<Adjustment> findByAdjustedBy(Long adjustedBy);

    List<Adjustment> findByAdjustmentType(Adjustment.AdjustmentType adjustmentType);
}