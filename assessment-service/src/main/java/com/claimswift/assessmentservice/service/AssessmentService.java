package com.claimswift.assessmentservice.service;

import com.claimswift.assessmentservice.client.ClaimServiceClient;
import com.claimswift.assessmentservice.client.DocumentServiceClient;
import com.claimswift.assessmentservice.client.PaymentServiceClient;
import com.claimswift.assessmentservice.dto.AdjustmentRequest;
import com.claimswift.assessmentservice.dto.AdjustmentResponse;
import com.claimswift.assessmentservice.dto.AssessmentRequest;
import com.claimswift.assessmentservice.dto.AssessmentResponse;
import com.claimswift.assessmentservice.dto.DecisionRequest;
import com.claimswift.assessmentservice.entity.Adjustment;
import com.claimswift.assessmentservice.entity.Assessment;
import com.claimswift.assessmentservice.repository.AdjustmentRepository;
import com.claimswift.assessmentservice.repository.AssessmentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final ClaimServiceClient claimServiceClient;
    private final DocumentServiceClient documentServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Transactional
    public AssessmentResponse assessClaim(AssessmentRequest request, Long assessorId, String username) {
        Assessment assessment = assessmentRepository.findByClaimId(request.getClaimId())
                .orElseGet(Assessment::new);

        assessment.setClaimId(request.getClaimId());
        assessment.setAssessorId(assessorId);
        assessment.setAssessedAmount(request.getAssessedAmount());
        assessment.setRecommendedAmount(request.getAssessedAmount());
        assessment.setJustification(request.getJustification());
        assessment.setNotes(request.getNotes());
        assessment.setDecision(Assessment.AssessmentDecision.PENDING_REVIEW);
        assessment.setValidationStatus(Assessment.ValidationStatus.VALID);
        assessment.setRiskLevel(Assessment.RiskLevel.MEDIUM);
        assessment.setRiskScore(BigDecimal.valueOf(50));
        assessment.setCreatedBy(assessment.getCreatedBy() == null ? username : assessment.getCreatedBy());
        assessment.setUpdatedBy(username);
        Assessment savedAssessment = assessmentRepository.save(assessment);

        // Keep claim progression aligned once assessor starts work.
        transitionClaimToUnderReview(savedAssessment.getClaimId(), username);

        return toAssessmentResponse(savedAssessment);
    }

    @Transactional
    public AssessmentResponse makeDecision(DecisionRequest request, Long assessorId, String username) {
        Assessment assessment = assessmentRepository.findById(request.getAssessmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + request.getAssessmentId()));
        ensureDocumentsExist(assessment.getClaimId());

        BigDecimal finalAmount = request.getFinalAmount() != null
                ? request.getFinalAmount()
                : assessment.getAssessedAmount();

        assessment.setAssessorId(assessorId);
        assessment.setDecision(request.getDecision());
        assessment.setAssessedAmount(finalAmount);
        assessment.setRecommendedAmount(finalAmount);
        assessment.setJustification(request.getJustification());
        assessment.setUpdatedBy(username);
        Assessment saved = assessmentRepository.save(assessment);

        syncClaimStatusWithDecision(saved);
        processPaymentIfApproved(saved);

        return toAssessmentResponse(saved);
    }

    @Transactional
    public AdjustmentResponse addAdjustment(AdjustmentRequest request, Long adjustedBy, String username) {
        Assessment assessment = assessmentRepository.findById(request.getAssessmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + request.getAssessmentId()));

        BigDecimal previousAmount = assessment.getAssessedAmount();
        BigDecimal adjustedAmount = request.getAdjustedAmount();
        BigDecimal differenceAmount = adjustedAmount.subtract(previousAmount == null ? BigDecimal.ZERO : previousAmount);

        Adjustment adjustment = Adjustment.builder()
                .assessmentId(request.getAssessmentId())
                .claimId(request.getClaimId())
                .previousAmount(previousAmount)
                .adjustedAmount(adjustedAmount)
                .differenceAmount(differenceAmount)
                .adjustmentType(request.getAdjustmentType())
                .reason(request.getReason())
                .detailedNotes(request.getDetailedNotes())
                .adjustedBy(adjustedBy)
                .createdBy(username)
                .updatedBy(username)
                .build();

        Adjustment savedAdjustment = adjustmentRepository.save(adjustment);

        assessment.setAssessedAmount(adjustedAmount);
        assessment.setRecommendedAmount(adjustedAmount);
        assessment.setDecision(Assessment.AssessmentDecision.ADJUSTED);
        assessment.setUpdatedBy(username);
        assessmentRepository.save(assessment);
        updateClaimStatus(assessment.getClaimId(), "ADJUSTED", adjustedAmount, username);

        return toAdjustmentResponse(savedAdjustment);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getAssessment(Long id) {
        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
        return toAssessmentResponse(assessment);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getAssessmentByClaim(Long claimId) {
        Assessment assessment = assessmentRepository.findByClaimId(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found for claim: " + claimId));
        return toAssessmentResponse(assessment);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> getAssessmentsByAssessor(Long assessorId) {
        return assessmentRepository.findByAssessorId(assessorId)
                .stream()
                .map(this::toAssessmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> getAdjustmentsByAssessment(Long assessmentId) {
        return adjustmentRepository.findByAssessmentId(assessmentId)
                .stream()
                .map(this::toAdjustmentResponse)
                .toList();
    }

    private AssessmentResponse toAssessmentResponse(Assessment assessment) {
        AssessmentResponse response = new AssessmentResponse();
        response.setId(assessment.getId());
        response.setClaimId(assessment.getClaimId());
        response.setAssessorId(assessment.getAssessorId());
        response.setRiskScore(assessment.getRiskScore());
        response.setRiskLevel(assessment.getRiskLevel());
        response.setDecision(assessment.getDecision());
        response.setAssessedAmount(assessment.getAssessedAmount());
        response.setRecommendedAmount(assessment.getRecommendedAmount());
        response.setJustification(assessment.getJustification());
        response.setNotes(assessment.getNotes());
        response.setValidationStatus(assessment.getValidationStatus());
        response.setRuleViolations(assessment.getRuleViolations());
        response.setCreatedAt(assessment.getCreatedAt());
        response.setUpdatedAt(assessment.getUpdatedAt());
        response.setAdjustments(getAdjustmentsByAssessment(assessment.getId()));
        return response;
    }

    private AdjustmentResponse toAdjustmentResponse(Adjustment adjustment) {
        AdjustmentResponse response = new AdjustmentResponse();
        response.setId(adjustment.getId());
        response.setAssessmentId(adjustment.getAssessmentId());
        response.setClaimId(adjustment.getClaimId());
        response.setPreviousAmount(adjustment.getPreviousAmount());
        response.setAdjustedAmount(adjustment.getAdjustedAmount());
        response.setDifferenceAmount(adjustment.getDifferenceAmount());
        response.setAdjustmentType(adjustment.getAdjustmentType());
        response.setReason(adjustment.getReason());
        response.setDetailedNotes(adjustment.getDetailedNotes());
        response.setAdjustedBy(adjustment.getAdjustedBy());
        response.setCreatedAt(adjustment.getCreatedAt());
        response.setUpdatedAt(adjustment.getUpdatedAt());
        return response;
    }

    private void ensureDocumentsExist(Long claimId) {
        Map<String, Object> response = documentServiceClient.getClaimDocumentCount(claimId);
        long documentCount = toLong(response.get("documentCount"));
        if (documentCount <= 0) {
            throw new IllegalStateException("At least one document is required before claim decision");
        }
    }

    private void syncClaimStatusWithDecision(Assessment assessment) {
        String status = mapDecisionToClaimStatus(assessment.getDecision());
        if (status == null) {
            return;
        }

        BigDecimal approvedAmount = "APPROVED".equals(status) || "ADJUSTED".equals(status)
                ? assessment.getRecommendedAmount()
                : null;
        updateClaimStatus(assessment.getClaimId(), status, approvedAmount, assessment.getUpdatedBy());
    }

    private void processPaymentIfApproved(Assessment assessment) {
        if (assessment.getDecision() != Assessment.AssessmentDecision.APPROVED) {
            return;
        }

        Map<String, Object> claim = claimServiceClient.getClaimForWorkflow(assessment.getClaimId());
        Long policyholderId = toLongObj(claim.get("policyholderId"));
        if (policyholderId == null) {
            throw new IllegalStateException("Claim policyholderId is missing for claim " + assessment.getClaimId());
        }

        BigDecimal amount = assessment.getRecommendedAmount() != null
                ? assessment.getRecommendedAmount()
                : toBigDecimal(claim.get("approvedAmount"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = toBigDecimal(claim.get("claimAmount"));
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Approved amount is missing for payment processing");
        }

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("claimId", assessment.getClaimId());
        paymentRequest.put("policyholderId", policyholderId);
        paymentRequest.put("amount", amount);
        paymentRequest.put("claimNumber", String.valueOf(claim.getOrDefault("claimNumber", assessment.getClaimId())));
        paymentRequest.put("forceSuccess", true);

        paymentServiceClient.autoProcessPayment(paymentRequest);
    }

    private void transitionClaimToUnderReview(Long claimId, String username) {
        try {
            updateClaimStatus(claimId, "UNDER_REVIEW", null, username);
        } catch (Exception ex) {
            log.debug("Claim {} transition to UNDER_REVIEW skipped: {}", claimId, ex.getMessage());
        }
    }

    private void updateClaimStatus(Long claimId, String status, BigDecimal approvedAmount, String username) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("notes", "Synced from assessment-service by " + username);
        if (approvedAmount != null) {
            update.put("approvedAmount", approvedAmount);
        }
        try {
            claimServiceClient.updateClaimStatus(claimId, update);
        } catch (Exception ex) {
            // Idempotency guard: once claim is already advanced (e.g. PAID), do not fail retries.
            if (isInvalidTransitionError(ex)) {
                log.info("Skipping duplicate transition for claim {} -> {}: {}", claimId, status, ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private String mapDecisionToClaimStatus(Assessment.AssessmentDecision decision) {
        return switch (decision) {
            case APPROVED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case ADJUSTED -> "ADJUSTED";
            default -> null;
        };
    }

    private long toLong(Object value) {
        Long parsed = toLongObj(value);
        return parsed == null ? 0L : parsed;
    }

    private Long toLongObj(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isInvalidTransitionError(Exception ex) {
        String marker = "Invalid claim status transition";
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains(marker)) {
                return true;
            }
            if (cursor instanceof FeignException feignException) {
                String body = feignException.contentUTF8();
                if (body != null && body.contains(marker)) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
