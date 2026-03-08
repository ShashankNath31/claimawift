package com.claimswift.assessmentservice.controller;

import com.claimswift.assessmentservice.dto.AdjustmentRequest;
import com.claimswift.assessmentservice.dto.AdjustmentResponse;
import com.claimswift.assessmentservice.dto.AssessmentRequest;
import com.claimswift.assessmentservice.dto.AssessmentResponse;
import com.claimswift.assessmentservice.dto.DecisionRequest;
import com.claimswift.assessmentservice.service.AssessmentService;
import com.claimswift.assessmentservice.util.StandardResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/assessments")
@Validated
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<AssessmentResponse>> assessClaim(
            @Valid @RequestBody AssessmentRequest request,
            @RequestAttribute("userId") Long assessorId,
            @RequestAttribute("username") String username) {
        AssessmentResponse response = assessmentService.assessClaim(request, assessorId, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.success("Claim assessed successfully", response));
    }

    @PostMapping("/decision")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<AssessmentResponse>> makeDecision(
            @Valid @RequestBody DecisionRequest request,
            @RequestAttribute("userId") Long assessorId,
            @RequestAttribute("username") String username) {
        AssessmentResponse response = assessmentService.makeDecision(request, assessorId, username);
        return ResponseEntity.ok(StandardResponse.success("Decision recorded successfully", response));
    }

    @PostMapping("/adjustment")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<AdjustmentResponse>> addAdjustment(
            @Valid @RequestBody AdjustmentRequest request,
            @RequestAttribute("userId") Long adjustedBy,
            @RequestAttribute("username") String username) {
        AdjustmentResponse response = assessmentService.addAdjustment(request, adjustedBy, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StandardResponse.success("Adjustment added successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<AssessmentResponse>> getAssessment(
            @PathVariable("id") @Positive(message = "Assessment ID must be positive") Long id) {
        AssessmentResponse response = assessmentService.getAssessment(id);
        return ResponseEntity.ok(StandardResponse.success(response));
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<AssessmentResponse>> getAssessmentByClaim(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId) {
        AssessmentResponse response = assessmentService.getAssessmentByClaim(claimId);
        return ResponseEntity.ok(StandardResponse.success(response));
    }

    @GetMapping("/my-assessments")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<List<AssessmentResponse>>> getMyAssessments(
            @RequestAttribute("userId") Long assessorId) {
        List<AssessmentResponse> responses = assessmentService.getAssessmentsByAssessor(assessorId);
        return ResponseEntity.ok(StandardResponse.success(responses));
    }

    @GetMapping("/{assessmentId}/adjustments")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<List<AdjustmentResponse>>> getAdjustments(
            @PathVariable("assessmentId") @Positive(message = "Assessment ID must be positive") Long assessmentId) {
        List<AdjustmentResponse> responses = assessmentService.getAdjustmentsByAssessment(assessmentId);
        return ResponseEntity.ok(StandardResponse.success(responses));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Assessment Service is running");
    }

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<Map<String, Object>>> requestAssessment(
            @RequestParam("claimId") @Positive(message = "Claim ID must be positive") Long claimId) {
        return ResponseEntity.ok(StandardResponse.success("Assessment request accepted",
                Map.of("claimId", claimId, "status", "REQUESTED")));
    }

    @PostMapping("/notify-complete")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER')")
    public ResponseEntity<StandardResponse<Void>> notifyAssessmentComplete(
            @RequestBody Map<String, Object> assessmentData) {
        log.info("Assessment completion notification received: {}", assessmentData);
        return ResponseEntity.ok(StandardResponse.success("Assessment completion acknowledged", null));
    }
}
