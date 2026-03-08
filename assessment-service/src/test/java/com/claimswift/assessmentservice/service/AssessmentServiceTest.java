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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;
    @Mock
    private AdjustmentRepository adjustmentRepository;
    @Mock
    private ClaimServiceClient claimServiceClient;
    @Mock
    private DocumentServiceClient documentServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    @InjectMocks
    private AssessmentService assessmentService;

    @Test
    void assessClaimCreatesAssessmentAndTransitionsClaim() {
        AssessmentRequest request = new AssessmentRequest();
        request.setClaimId(101L);
        request.setAssessedAmount(new BigDecimal("1200.00"));
        request.setJustification("Damage verified");
        request.setNotes("notes");

        when(assessmentRepository.findByClaimId(101L)).thenReturn(Optional.empty());
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> {
            Assessment saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(adjustmentRepository.findByAssessmentId(1L)).thenReturn(List.of());

        AssessmentResponse response = assessmentService.assessClaim(request, 99L, "assessor");

        assertNotNull(response);
        assertEquals(101L, response.getClaimId());
        assertEquals(Assessment.AssessmentDecision.PENDING_REVIEW, response.getDecision());
        assertEquals(Assessment.ValidationStatus.VALID, response.getValidationStatus());
        assertEquals(new BigDecimal("50"), response.getRiskScore());
        verify(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
    }

    @Test
    void assessClaimPreservesExistingCreatedBy() {
        Assessment existing = Assessment.builder()
                .id(5L)
                .claimId(101L)
                .createdBy("original-user")
                .build();

        AssessmentRequest request = new AssessmentRequest();
        request.setClaimId(101L);
        request.setAssessedAmount(new BigDecimal("100.00"));

        when(assessmentRepository.findByClaimId(101L)).thenReturn(Optional.of(existing));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentRepository.findByAssessmentId(5L)).thenReturn(List.of());

        assessmentService.assessClaim(request, 33L, "new-user");

        ArgumentCaptor<Assessment> captor = ArgumentCaptor.forClass(Assessment.class);
        verify(assessmentRepository).save(captor.capture());
        assertEquals("original-user", captor.getValue().getCreatedBy());
        assertEquals("new-user", captor.getValue().getUpdatedBy());
    }

    @Test
    void makeDecisionThrowsWhenDocumentsAreMissing() {
        Assessment assessment = baseAssessment();
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.APPROVED);

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 0));

        assertThrows(IllegalStateException.class, () -> assessmentService.makeDecision(request, 44L, "assessor"));
        verify(assessmentRepository, never()).save(any(Assessment.class));
    }

    @Test
    void makeDecisionApprovedTriggersClaimSyncAndPayment() {
        Assessment assessment = baseAssessment();
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.APPROVED);
        request.setFinalAmount(new BigDecimal("900.00"));
        request.setJustification("Approved");

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 2));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of());
        when(claimServiceClient.getClaimForWorkflow(assessment.getClaimId()))
                .thenReturn(Map.of(
                        "policyholderId", 777L,
                        "claimNumber", "CLM-777",
                        "claimAmount", new BigDecimal("900.00")
                ));

        AssessmentResponse response = assessmentService.makeDecision(request, 50L, "assessor");

        assertEquals(Assessment.AssessmentDecision.APPROVED, response.getDecision());
        verify(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
        verify(paymentServiceClient).autoProcessPayment(any(Map.class));
    }

    @Test
    void makeDecisionRejectedDoesNotProcessPayment() {
        Assessment assessment = baseAssessment();
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.REJECTED);
        request.setFinalAmount(new BigDecimal("300.00"));

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 1));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of());

        AssessmentResponse response = assessmentService.makeDecision(request, 4L, "assessor");

        assertEquals(Assessment.AssessmentDecision.REJECTED, response.getDecision());
        verify(paymentServiceClient, never()).autoProcessPayment(any(Map.class));
    }

    @Test
    void makeDecisionSkipsInvalidClaimTransitionError() {
        Assessment assessment = baseAssessment();
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.APPROVED);

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 1));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of());
        doThrow(new RuntimeException("Invalid claim status transition from PAID to APPROVED"))
                .when(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
        when(claimServiceClient.getClaimForWorkflow(assessment.getClaimId()))
                .thenReturn(Map.of("policyholderId", 1L, "claimAmount", new BigDecimal("100.00")));

        AssessmentResponse response = assessmentService.makeDecision(request, 8L, "assessor");
        assertEquals(Assessment.AssessmentDecision.APPROVED, response.getDecision());
    }

    @Test
    void makeDecisionApprovedThrowsWhenPolicyholderMissing() {
        Assessment assessment = baseAssessment();
        assessment.setAssessedAmount(null);
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.APPROVED);

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 3));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimServiceClient.getClaimForWorkflow(assessment.getClaimId()))
                .thenReturn(Map.of("claimAmount", new BigDecimal("350.00")));

        assertThrows(IllegalStateException.class, () -> assessmentService.makeDecision(request, 2L, "assessor"));
    }

    @Test
    void addAdjustmentUpdatesAssessmentAndClaimStatus() {
        Assessment assessment = baseAssessment();
        AdjustmentRequest request = new AdjustmentRequest();
        request.setAssessmentId(1L);
        request.setClaimId(assessment.getClaimId());
        request.setAdjustedAmount(new BigDecimal("1300.00"));
        request.setAdjustmentType(Adjustment.AdjustmentType.OTHER);
        request.setReason("manual update");
        request.setDetailedNotes("detail");

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(adjustmentRepository.save(any(Adjustment.class))).thenAnswer(invocation -> {
            Adjustment saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdjustmentResponse response = assessmentService.addAdjustment(request, 88L, "reviewer");

        assertEquals(new BigDecimal("300.00"), response.getDifferenceAmount());
        verify(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
    }

    @Test
    void getAssessmentByClaimThrowsWhenMissing() {
        when(assessmentRepository.findByClaimId(444L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> assessmentService.getAssessmentByClaim(444L));
    }

    @Test
    void getAssessmentsAndAdjustmentsMapResponses() {
        Assessment assessment = baseAssessment();
        Adjustment adjustment = Adjustment.builder()
                .id(9L)
                .assessmentId(1L)
                .claimId(assessment.getClaimId())
                .adjustedAmount(new BigDecimal("999.00"))
                .build();

        when(assessmentRepository.findByAssessorId(assessment.getAssessorId())).thenReturn(List.of(assessment));
        when(adjustmentRepository.findByAssessmentId(1L)).thenReturn(List.of(adjustment));

        List<AssessmentResponse> assessments = assessmentService.getAssessmentsByAssessor(assessment.getAssessorId());
        List<AdjustmentResponse> adjustments = assessmentService.getAdjustmentsByAssessment(1L);

        assertEquals(1, assessments.size());
        assertEquals(1, adjustments.size());
        assertEquals(9L, adjustments.get(0).getId());
    }

    @Test
    void makeDecisionThrowsWhenAssessmentNotFound() {
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(99L);
        request.setDecision(Assessment.AssessmentDecision.REJECTED);
        when(assessmentRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> assessmentService.makeDecision(request, 2L, "user")
        );
        assertNotNull(ex.getMessage());
    }

    @Test
    void approvedDecisionFallsBackToClaimAmountWhenFinalAndAssessedAreMissing() {
        Assessment assessment = baseAssessment();
        assessment.setAssessedAmount(null);
        DecisionRequest request = new DecisionRequest();
        request.setAssessmentId(1L);
        request.setDecision(Assessment.AssessmentDecision.APPROVED);
        request.setFinalAmount(null);

        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(documentServiceClient.getClaimDocumentCount(assessment.getClaimId()))
                .thenReturn(Map.of("documentCount", 1));
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustmentRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of());
        when(claimServiceClient.getClaimForWorkflow(assessment.getClaimId()))
                .thenReturn(Map.of(
                        "policyholderId", 15L,
                        "approvedAmount", BigDecimal.ZERO,
                        "claimAmount", new BigDecimal("450.00")
                ));

        AssessmentResponse response = assessmentService.makeDecision(request, 8L, "approver");

        assertNull(response.getAssessedAmount());
        verify(paymentServiceClient).autoProcessPayment(any(Map.class));
    }

    private Assessment baseAssessment() {
        return Assessment.builder()
                .id(1L)
                .claimId(300L)
                .assessorId(5L)
                .assessedAmount(new BigDecimal("1000.00"))
                .recommendedAmount(new BigDecimal("1000.00"))
                .decision(Assessment.AssessmentDecision.PENDING_REVIEW)
                .updatedBy("seed")
                .build();
    }
}
