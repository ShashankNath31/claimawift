package com.claimswift.claimservice.service;

import com.claimswift.claimservice.client.AssessmentServiceClient;
import com.claimswift.claimservice.client.AuthServiceClient;
import com.claimswift.claimservice.client.NotificationServiceClient;
import com.claimswift.claimservice.client.ReportingServiceClient;
import com.claimswift.claimservice.dto.ClaimAuditEventResponse;
import com.claimswift.claimservice.dto.ClaimBankDetailsRequest;
import com.claimswift.claimservice.dto.ClaimBankDetailsResponse;
import com.claimswift.claimservice.dto.ClaimRequest;
import com.claimswift.claimservice.dto.ClaimResponse;
import com.claimswift.claimservice.dto.ClaimStatusUpdateRequest;
import com.claimswift.claimservice.dto.PolicyPortfolioResponse;
import com.claimswift.claimservice.dto.PolicyResponse;
import com.claimswift.claimservice.entity.ClaimAuditEvent;
import com.claimswift.claimservice.entity.Claim;
import com.claimswift.claimservice.entity.Policy;
import com.claimswift.claimservice.mapper.ClaimMapper;
import com.claimswift.claimservice.repository.ClaimAuditEventRepository;
import com.claimswift.claimservice.repository.ClaimRepository;
import com.claimswift.claimservice.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimMapper claimMapper;
    private final AssessmentServiceClient assessmentServiceClient;
    private final AuthServiceClient authServiceClient;
    private final ReportingServiceClient reportingServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final PolicyRepository policyRepository;
    private final ClaimAuditEventRepository claimAuditEventRepository;
    private final Map<Claim.ClaimStatus, Set<Claim.ClaimStatus>> allowedTransitions = buildAllowedTransitions();

    @Transactional
    public ClaimResponse submitClaim(ClaimRequest request, Long claimantId, String username) {
        Policy policy = resolvePolicyForClaimSubmission(request, claimantId);

        Claim claim = claimMapper.toEntity(request);
        claim.setClaimNumber(generateClaimNumber());
        claim.setPolicyNumber(policy.getPolicyNumber());
        claim.setVehicleRegistration(policy.getVehicleRegistration());
        claim.setClaimantId(claimantId);
        applyClaimantSnapshot(claim, username);
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);
        claim.setCreatedBy(username);
        claim.setUpdatedBy(username);

        Claim savedClaim = claimRepository.save(claim);
        log.info("Claim submitted: {}", savedClaim.getClaimNumber());
        recordAuditEvent(
                savedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_SUBMITTED,
                null,
                Claim.ClaimStatus.SUBMITTED,
                username,
                "Claim submitted by policyholder",
                savedClaim.getAdjusterId(),
                savedClaim.getApprovedAmount()
        );

        // Report to Reporting Service
        reportClaimEvent(savedClaim, "CLAIM_SUBMITTED");
        notifyClaimStatus(savedClaim, Claim.ClaimStatus.SUBMITTED.name());

        return claimMapper.toResponse(savedClaim);
    }

    @Transactional
    public PolicyPortfolioResponse getMyPolicies(Long policyholderId) {
        ensureDefaultPolicies(policyholderId);

        List<Policy> policies = policyRepository.findByPolicyholderIdOrderByExpiryDateDesc(policyholderId);
        LocalDate today = LocalDate.now();
        long activeCount = 0;
        long expiredCount = 0;
        List<PolicyResponse> policyResponses = new ArrayList<>();

        for (Policy policy : policies) {
            String status = policy.getExpiryDate().isBefore(today) ? "EXPIRED" : "ACTIVE";
            if ("ACTIVE".equals(status)) {
                activeCount++;
            } else {
                expiredCount++;
            }

            BigDecimal claimedAmount = claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(
                    policy.getPolicyNumber(),
                    List.of(Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.CANCELLED)
            );
            BigDecimal available = policy.getCoverageAmount().subtract(claimedAmount == null ? BigDecimal.ZERO : claimedAmount);
            if (available.compareTo(BigDecimal.ZERO) < 0) {
                available = BigDecimal.ZERO;
            }

            policyResponses.add(PolicyResponse.builder()
                    .id(policy.getId())
                    .policyNumber(policy.getPolicyNumber())
                    .policyType(policy.getPolicyType())
                    .planName(policy.getPlanName())
                    .insurerName(policy.getInsurerName())
                    .vehicleRegistration(policy.getVehicleRegistration())
                    .coverageAmount(policy.getCoverageAmount())
                    .claimedAmount(claimedAmount == null ? BigDecimal.ZERO : claimedAmount)
                    .availableCoverage(available)
                    .startDate(policy.getStartDate())
                    .expiryDate(policy.getExpiryDate())
                    .status(status)
                    .build());
        }

        return PolicyPortfolioResponse.builder()
                .totalPolicies((long) policyResponses.size())
                .activePolicies(activeCount)
                .expiredPolicies(expiredCount)
                .policies(policyResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimById(Long id) {
        return getClaimById(id, null);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimById(Long id, Long requesterUserId) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
        assertCanAccessClaim(claim, requesterUserId);
        return claimMapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimByNumber(String claimNumber) {
        return getClaimByNumber(claimNumber, null);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimByNumber(String claimNumber, Long requesterUserId) {
        Claim claim = claimRepository.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new RuntimeException("Claim not found with number: " + claimNumber));
        assertCanAccessClaim(claim, requesterUserId);
        return claimMapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getClaimsByClaimant(Long claimantId) {
        List<Claim> claims = claimRepository.findByClaimantId(claimantId);
        return claimMapper.toResponseList(claims);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getClaimHistory(Long claimantId) {
        List<Claim> claims = claimRepository.findByClaimantId(claimantId);
        return claimMapper.toResponseList(claims);
    }

    @Transactional
    public ClaimBankDetailsResponse upsertClaimBankDetails(
            Long claimId,
            ClaimBankDetailsRequest request,
            Long requesterUserId,
            String username
    ) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));

        if (requesterUserId == null || !claim.getClaimantId().equals(requesterUserId)) {
            throw new AccessDeniedException("You are not allowed to update bank details for this claim.");
        }

        if (claim.getStatus() != Claim.ClaimStatus.APPROVED && claim.getStatus() != Claim.ClaimStatus.PAYMENT_FAILED) {
            throw new IllegalArgumentException("Bank details can be updated only after claim approval.");
        }

        claim.setBeneficiaryName(request.getBeneficiaryName().trim());
        claim.setBankAccountNumber(request.getAccountNumber().trim());
        claim.setIfscCode(request.getIfscCode().trim().toUpperCase());
        claim.setBankName(request.getBankName().trim());
        claim.setBankDetailsUpdatedAt(LocalDateTime.now());
        claim.setUpdatedBy(username);

        Claim updatedClaim = claimRepository.save(claim);
        recordAuditEvent(
                updatedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_UPDATED,
                updatedClaim.getStatus(),
                updatedClaim.getStatus(),
                username,
                "Policyholder updated settlement bank details",
                updatedClaim.getAdjusterId(),
                updatedClaim.getApprovedAmount()
        );

        return toBankDetailsResponse(updatedClaim);
    }

    @Transactional(readOnly = true)
    public ClaimBankDetailsResponse getClaimBankDetails(Long claimId, Long requesterUserId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));

        if (isAdjusterOnly()) {
            throw new AccessDeniedException("Adjuster cannot access policyholder bank details.");
        }

        if (isPolicyholderOnly() && (requesterUserId == null || !claim.getClaimantId().equals(requesterUserId))) {
            throw new AccessDeniedException("You are not allowed to access bank details for this claim.");
        }

        if (!hasSettlementBankDetails(claim)) {
            throw new IllegalArgumentException("Bank details are not available for this claim yet.");
        }

        return toBankDetailsResponse(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimAuditEventResponse> getClaimAuditTrail(Long claimId) {
        return getClaimAuditTrail(claimId, null);
    }

    @Transactional(readOnly = true)
    public List<ClaimAuditEventResponse> getClaimAuditTrail(Long claimId, Long requesterUserId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));
        assertCanAccessClaim(claim, requesterUserId);
        return claimAuditEventRepository.findByClaimIdOrderByCreatedAtDesc(claimId).stream()
                .map(this::toAuditResponse)
                .toList();
    }

    @Transactional
    public ClaimResponse updateClaimStatus(Long claimId, ClaimStatusUpdateRequest request, Long requesterUserId, String username) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));
        assertCanUpdateClaim(claim, requesterUserId);

        Claim.ClaimStatus newStatus = request.getStatus();
        Claim.ClaimStatus oldStatus = claim.getStatus();
        validateRoleBasedStatusUpdate(newStatus);
        validateStatusTransition(oldStatus, newStatus);

        claim.updateStatus(newStatus, username);

        if (request.getAssignedAdjusterId() != null) {
            claim.setAdjusterId(request.getAssignedAdjusterId());
        }

        applyApprovedAmount(claim, newStatus, request.getApprovedAmount());

        Claim updatedClaim = claimRepository.save(claim);
        log.info("Claim {} status updated from {} to {}", claim.getClaimNumber(), oldStatus, newStatus);
        recordAuditEvent(
                updatedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_STATUS_UPDATED,
                oldStatus,
                newStatus,
                username,
                request.getNotes(),
                updatedClaim.getAdjusterId(),
                updatedClaim.getApprovedAmount()
        );

        // Report status change to Reporting Service
        reportStatusChange(updatedClaim, oldStatus.name(), newStatus.name());
        notifyClaimStatus(updatedClaim, newStatus.name());
        if (newStatus == Claim.ClaimStatus.ADJUSTED && isAdjusterOnly()) {
            notifyManagersReviewRequested(updatedClaim, username, request.getNotes());
        }

        // Notify Assessment Service if approved
        if (newStatus == Claim.ClaimStatus.APPROVED) {
            notifyAssessmentComplete(updatedClaim);
        }

        return claimMapper.toResponse(updatedClaim);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getClaimsByStatus(String status) {
        Claim.ClaimStatus claimStatus = Claim.ClaimStatus.valueOf(status);
        List<Claim> claims = claimRepository.findByStatus(claimStatus);
        return claimMapper.toResponseList(claims);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getClaimsPage(int page, int size, String status, String sortBy, String sortDir) {
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Claim> claimsPage;
        if (status != null && !status.isBlank()) {
            Claim.ClaimStatus claimStatus = Claim.ClaimStatus.valueOf(status.toUpperCase());
            claimsPage = claimRepository.findByStatus(claimStatus, pageable);
        } else {
            claimsPage = claimRepository.findAll(pageable);
        }

        return claimsPage.map(claimMapper::toResponse);
    }

    @Transactional
    public ClaimResponse updateClaim(Long id, ClaimRequest request, String username) {
        Claim claim = claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
        claimMapper.updateEntityFromRequest(request, claim);
        claim.setUpdatedBy(username);
        Claim updatedClaim = claimRepository.save(claim);
        recordAuditEvent(
                updatedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_UPDATED,
                updatedClaim.getStatus(),
                updatedClaim.getStatus(),
                username,
                "Claim details updated",
                updatedClaim.getAdjusterId(),
                updatedClaim.getApprovedAmount()
        );
        return claimMapper.toResponse(updatedClaim);
    }

    @Transactional
    public void deleteClaim(Long id) {
        if (!claimRepository.existsById(id)) {
            throw new RuntimeException("Claim not found with id: " + id);
        }
        claimRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getAssignedClaims(Long adjusterId) {
        return claimMapper.toResponseList(claimRepository.findByStatusAndAdjusterId(Claim.ClaimStatus.UNDER_REVIEW, adjusterId));
    }

    @Transactional
    public ClaimResponse assignClaim(Long claimId, Long adjusterId, String username) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));

        Claim.ClaimStatus oldStatus = claim.getStatus();
        validateStatusTransition(claim.getStatus(), Claim.ClaimStatus.UNDER_REVIEW);
        claim.setAdjusterId(adjusterId);
        claim.updateStatus(Claim.ClaimStatus.UNDER_REVIEW, username);
        Claim updatedClaim = claimRepository.save(claim);
        recordAuditEvent(
                updatedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_ASSIGNED,
                oldStatus,
                updatedClaim.getStatus(),
                username,
                "Assigned to adjuster " + adjusterId,
                adjusterId,
                updatedClaim.getApprovedAmount()
        );
        notifyAdjusterAssignment(updatedClaim, adjusterId, username);
        return claimMapper.toResponse(updatedClaim);
    }

    @Transactional
    public ClaimResponse unassignClaim(Long claimId, String username) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + claimId));

        Claim.ClaimStatus oldStatus = claim.getStatus();
        claim.setAdjusterId(null);

        if (oldStatus == Claim.ClaimStatus.UNDER_REVIEW) {
            claim.updateStatus(Claim.ClaimStatus.SUBMITTED, username);
        } else {
            claim.setUpdatedBy(username);
        }

        Claim updatedClaim = claimRepository.save(claim);
        recordAuditEvent(
                updatedClaim,
                ClaimAuditEvent.AuditAction.CLAIM_UNASSIGNED,
                oldStatus,
                updatedClaim.getStatus(),
                username,
                "Adjuster unassigned",
                null,
                updatedClaim.getApprovedAmount()
        );
        if (oldStatus != updatedClaim.getStatus()) {
            reportStatusChange(updatedClaim, oldStatus.name(), updatedClaim.getStatus().name());
            notifyClaimStatus(updatedClaim, updatedClaim.getStatus().name());
        }

        return claimMapper.toResponse(updatedClaim);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClaimStatistics() {
        long total = claimRepository.count();
        long submitted = claimRepository.countByStatus(Claim.ClaimStatus.SUBMITTED);
        long underReview = claimRepository.countByStatus(Claim.ClaimStatus.UNDER_REVIEW);
        long approved = claimRepository.countByStatus(Claim.ClaimStatus.APPROVED);
        long rejected = claimRepository.countByStatus(Claim.ClaimStatus.REJECTED);
        long adjusted = claimRepository.countByStatus(Claim.ClaimStatus.ADJUSTED);
        long paid = claimRepository.countByStatus(Claim.ClaimStatus.PAID);
        long paymentFailed = claimRepository.countByStatus(Claim.ClaimStatus.PAYMENT_FAILED);
        long cancelled = claimRepository.countByStatus(Claim.ClaimStatus.CANCELLED);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalClaims", total);
        stats.put("submittedClaims", submitted);
        stats.put("underReviewClaims", underReview);
        stats.put("approvedClaims", approved);
        stats.put("rejectedClaims", rejected);
        stats.put("adjustedClaims", adjusted);
        stats.put("paidClaims", paid);
        stats.put("paymentFailedClaims", paymentFailed);
        stats.put("cancelledClaims", cancelled);
        stats.put("closedClaims", paid);
        stats.put("approvedAmount", claimRepository.sumApprovedClaims());
        return stats;
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> searchClaims(String query) {
        return claimMapper.toResponseList(claimRepository.searchClaims(query));
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> searchMyClaims(
            Long claimantId,
            String query,
            String status,
            String fromDate,
            String toDate
    ) {
        Claim.ClaimStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            parsedStatus = Claim.ClaimStatus.valueOf(status.trim().toUpperCase());
        }

        return claimMapper.toResponseList(claimRepository.searchMyClaims(
                claimantId,
                normalizeQuery(query),
                parsedStatus,
                parseDate(fromDate),
                parseDate(toDate)
        ));
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getAllClaims() {
        return claimMapper.toResponseList(claimRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getClaimsByAdjuster(Long adjusterId) {
        return claimMapper.toResponseList(claimRepository.findByAdjusterId(adjusterId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getClaimsSummary(String startDate, String endDate) {
        Map<String, Object> summary = new HashMap<>(getClaimStatistics());
        summary.put("startDate", startDate);
        summary.put("endDate", endDate);
        return summary;
    }

    private String generateClaimNumber() {
        return "CLM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Policy resolvePolicyForClaimSubmission(ClaimRequest request, Long claimantId) {
        ensureDefaultPolicies(claimantId);

        String requestedPolicyNumber = request.getPolicyNumber() == null ? "" : request.getPolicyNumber().trim();
        if (requestedPolicyNumber.isBlank()) {
            throw new IllegalArgumentException("Policy number is required for claim submission.");
        }

        Policy policy = policyRepository.findByPolicyNumberAndPolicyholderId(requestedPolicyNumber, claimantId)
                .orElseThrow(() -> new IllegalArgumentException("Selected policy does not belong to this policyholder."));

        LocalDate today = LocalDate.now();
        if (policy.getExpiryDate().isBefore(today)) {
            throw new IllegalArgumentException("Selected policy is expired and cannot be used for a new claim.");
        }

        BigDecimal claimedAmount = claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(
                policy.getPolicyNumber(),
                List.of(Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.CANCELLED)
        );
        BigDecimal used = claimedAmount == null ? BigDecimal.ZERO : claimedAmount;
        BigDecimal availableCoverage = policy.getCoverageAmount().subtract(used);
        if (availableCoverage.compareTo(BigDecimal.ZERO) < 0) {
            availableCoverage = BigDecimal.ZERO;
        }

        if (request.getClaimAmount().compareTo(availableCoverage) > 0) {
            throw new IllegalArgumentException(
                    "Requested claim amount exceeds available policy coverage. Available: " + availableCoverage
            );
        }

        return policy;
    }

    private void ensureDefaultPolicies(Long policyholderId) {
        if (policyRepository.existsByPolicyholderId(policyholderId)) {
            return;
        }

        LocalDate today = LocalDate.now();
        Policy activePolicyOne = Policy.builder()
                .policyNumber("POL-" + policyholderId + "-A1")
                .policyholderId(policyholderId)
                .policyType("Motor Insurance")
                .planName("Comprehensive Plus")
                .insurerName("ClaimSwift Insurance Co.")
                .vehicleRegistration("MH12AB1234")
                .coverageAmount(new BigDecimal("500000.00"))
                .startDate(today.minusMonths(6))
                .expiryDate(today.plusMonths(6))
                .build();

        Policy activePolicyTwo = Policy.builder()
                .policyNumber("POL-" + policyholderId + "-A2")
                .policyholderId(policyholderId)
                .policyType("Motor Insurance")
                .planName("Premium Shield")
                .insurerName("ClaimSwift Insurance Co.")
                .vehicleRegistration("DL08CD6789")
                .coverageAmount(new BigDecimal("750000.00"))
                .startDate(today.minusMonths(4))
                .expiryDate(today.plusMonths(10))
                .build();

        Policy renewalSoonPolicy = Policy.builder()
                .policyNumber("POL-" + policyholderId + "-R1")
                .policyholderId(policyholderId)
                .policyType("Motor Insurance")
                .planName("Standard Cover")
                .insurerName("ClaimSwift Insurance Co.")
                .vehicleRegistration("KA03EF2468")
                .coverageAmount(new BigDecimal("300000.00"))
                .startDate(today.minusMonths(11))
                .expiryDate(today.plusDays(18))
                .build();

        Policy expiredPolicy = Policy.builder()
                .policyNumber("POL-" + policyholderId + "-E1")
                .policyholderId(policyholderId)
                .policyType("Motor Insurance")
                .planName("Third Party Cover")
                .insurerName("ClaimSwift Insurance Co.")
                .vehicleRegistration("MH14XY4321")
                .coverageAmount(new BigDecimal("250000.00"))
                .startDate(today.minusYears(2))
                .expiryDate(today.minusMonths(2))
                .build();

        policyRepository.saveAll(List.of(activePolicyOne, activePolicyTwo, renewalSoonPolicy, expiredPolicy));
    }

    private void reportClaimEvent(Claim claim, String eventType) {
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("claimId", claim.getId());
            eventData.put("claimNumber", claim.getClaimNumber());
            eventData.put("eventType", eventType);
            eventData.put("timestamp", LocalDateTime.now());
            reportingServiceClient.reportClaimEvent(eventData);
        } catch (Exception e) {
            log.error("Failed to report claim event", e);
        }
    }

    private void reportStatusChange(Claim claim, String oldStatus, String newStatus) {
        try {
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("claimId", claim.getId());
            statusData.put("claimNumber", claim.getClaimNumber());
            statusData.put("oldStatus", oldStatus);
            statusData.put("newStatus", newStatus);
            statusData.put("timestamp", claim.getStatusChangedAt());
            reportingServiceClient.reportStatusChange(statusData);
        } catch (Exception e) {
            log.error("Failed to report status change", e);
        }
    }

    private void notifyAssessmentComplete(Claim claim) {
        try {
            Map<String, Object> assessmentData = new HashMap<>();
            assessmentData.put("claimId", claim.getId());
            assessmentData.put("claimNumber", claim.getClaimNumber());
            assessmentData.put("approvedAmount", claim.getApprovedAmount());
            assessmentServiceClient.notifyAssessmentComplete(assessmentData);
        } catch (Exception e) {
            log.error("Failed to notify assessment service", e);
        }
    }

    private void notifyClaimStatus(Claim claim, String status) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", claim.getClaimantId());
            payload.put("claimId", claim.getId());
            payload.put("claimNumber", claim.getClaimNumber());
            payload.put("status", status);
            notificationServiceClient.sendClaimStatusNotification(payload);
        } catch (Exception e) {
            log.error("Failed to notify notification service for claim {}", claim.getId(), e);
        }
    }

    private void notifyAdjusterAssignment(Claim claim, Long adjusterId, String assignedBy) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", adjusterId);
            payload.put("claimId", claim.getId());
            payload.put("title", "New Claim Assigned");
            payload.put(
                    "message",
                    String.format("Manager %s assigned claim %s for assessment.", assignedBy, claim.getClaimNumber())
            );
            payload.put("type", "SYSTEM_MESSAGE");
            payload.put("actionUrl", "/claims");
            payload.put("senderId", null);
            notificationServiceClient.sendNotification(payload);
        } catch (Exception e) {
            log.error("Failed to send assignment notification for claim {}", claim.getId(), e);
        }
    }

    private void notifyManagersReviewRequested(Claim claim, String requestedBy, String note) {
        List<Long> managerIds = getActiveManagerIds();
        if (managerIds.isEmpty()) {
            return;
        }
        String detail = toTrimmedString(note);
        for (Long managerId : managerIds) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", managerId);
                payload.put("claimId", claim.getId());
                payload.put("title", "Manager Review Requested");
                payload.put(
                        "message",
                        detail == null
                                ? String.format("Adjuster %s requested manager review for claim %s.", requestedBy, claim.getClaimNumber())
                                : String.format(
                                        "Adjuster %s requested manager review for claim %s. Note: %s",
                                        requestedBy,
                                        claim.getClaimNumber(),
                                        detail
                                )
                );
                payload.put("type", "SYSTEM_MESSAGE");
                payload.put("actionUrl", "/claims");
                payload.put("senderId", null);
                notificationServiceClient.sendNotification(payload);
            } catch (Exception e) {
                log.error("Failed to send manager review notification for claim {}", claim.getId(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> getActiveManagerIds() {
        try {
            Map<String, Object> envelope = authServiceClient.getManagers();
            if (envelope == null) {
                return List.of();
            }
            Object dataObject = envelope.get("data");
            if (!(dataObject instanceof List<?> managerRows)) {
                return List.of();
            }
            return managerRows.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(row -> "ACTIVE".equalsIgnoreCase(String.valueOf(row.get("status"))))
                    .map(row -> parseLong(row.get("id")))
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to fetch managers for review notification: {}", ex.getMessage());
            return List.of();
        }
    }

    private void applyApprovedAmount(Claim claim, Claim.ClaimStatus newStatus, BigDecimal requestedApprovedAmount) {
        if (requestedApprovedAmount != null) {
            claim.setApprovedAmount(requestedApprovedAmount);
            return;
        }

        if ((newStatus == Claim.ClaimStatus.APPROVED || newStatus == Claim.ClaimStatus.PAID)
                && claim.getApprovedAmount() == null
                && claim.getClaimAmount() != null) {
            claim.setApprovedAmount(claim.getClaimAmount());
        }
    }

    private void validateStatusTransition(Claim.ClaimStatus currentStatus, Claim.ClaimStatus newStatus) {
        Set<Claim.ClaimStatus> validTransitions = allowedTransitions.getOrDefault(currentStatus, EnumSet.noneOf(Claim.ClaimStatus.class));
        if (!validTransitions.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid claim status transition from " + currentStatus + " to " + newStatus);
        }
    }

    private void validateRoleBasedStatusUpdate(Claim.ClaimStatus newStatus) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean isAdjusterOnly = roles.contains("ROLE_ADJUSTER")
                && !roles.contains("ROLE_MANAGER")
                && !roles.contains("ROLE_ADMIN");
        if (!isAdjusterOnly) {
            return;
        }
        if (newStatus == Claim.ClaimStatus.PAID || newStatus == Claim.ClaimStatus.PAYMENT_FAILED) {
            throw new IllegalArgumentException("Adjuster is not allowed to set status to " + newStatus + ".");
        }
    }

    private void assertCanAccessClaim(Claim claim, Long requesterUserId) {
        if (isPolicyholderOnly()) {
            if (requesterUserId == null || !claim.getClaimantId().equals(requesterUserId)) {
                throw new AccessDeniedException("You are not allowed to access this claim.");
            }
            return;
        }

        if (isAdjusterOnly()) {
            if (requesterUserId == null || claim.getAdjusterId() == null || !claim.getAdjusterId().equals(requesterUserId)) {
                throw new AccessDeniedException("Adjuster can access only assigned claims.");
            }
        }
    }

    private void assertCanUpdateClaim(Claim claim, Long requesterUserId) {
        if (isAdjusterOnly()) {
            if (requesterUserId == null || claim.getAdjusterId() == null || !claim.getAdjusterId().equals(requesterUserId)) {
                throw new AccessDeniedException("Adjuster can update only assigned claims.");
            }
        }
    }

    private boolean isAdjusterOnly() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.contains("ROLE_ADJUSTER")
                && !roles.contains("ROLE_MANAGER")
                && !roles.contains("ROLE_ADMIN");
    }

    private boolean isPolicyholderOnly() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.contains("ROLE_POLICYHOLDER")
                && !roles.contains("ROLE_ADJUSTER")
                && !roles.contains("ROLE_MANAGER")
                && !roles.contains("ROLE_ADMIN");
    }

    private Map<Claim.ClaimStatus, Set<Claim.ClaimStatus>> buildAllowedTransitions() {
        Map<Claim.ClaimStatus, Set<Claim.ClaimStatus>> transitions = new EnumMap<>(Claim.ClaimStatus.class);
        transitions.put(Claim.ClaimStatus.SUBMITTED, EnumSet.of(Claim.ClaimStatus.UNDER_REVIEW, Claim.ClaimStatus.CANCELLED));
        transitions.put(Claim.ClaimStatus.UNDER_REVIEW, EnumSet.of(Claim.ClaimStatus.APPROVED, Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.ADJUSTED, Claim.ClaimStatus.CANCELLED));
        transitions.put(Claim.ClaimStatus.ADJUSTED, EnumSet.of(Claim.ClaimStatus.APPROVED, Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.CANCELLED));
        transitions.put(Claim.ClaimStatus.APPROVED, EnumSet.of(Claim.ClaimStatus.PAID, Claim.ClaimStatus.PAYMENT_FAILED));
        transitions.put(Claim.ClaimStatus.PAYMENT_FAILED, EnumSet.of(Claim.ClaimStatus.PAID, Claim.ClaimStatus.CANCELLED));
        transitions.put(Claim.ClaimStatus.REJECTED, EnumSet.noneOf(Claim.ClaimStatus.class));
        transitions.put(Claim.ClaimStatus.PAID, EnumSet.noneOf(Claim.ClaimStatus.class));
        transitions.put(Claim.ClaimStatus.CANCELLED, EnumSet.noneOf(Claim.ClaimStatus.class));
        return transitions;
    }

    @SuppressWarnings("unchecked")
    private void applyClaimantSnapshot(Claim claim, String fallbackUsername) {
        String claimantName = fallbackUsername;
        String claimantPhone = null;
        try {
            Map<String, Object> envelope = authServiceClient.getCurrentUser();
            if (envelope != null) {
                Object dataObject = envelope.get("data");
                if (dataObject instanceof Map<?, ?> dataMap) {
                    String firstName = toTrimmedString(dataMap.get("firstName"));
                    String lastName = toTrimmedString(dataMap.get("lastName"));
                    String fullName = (firstName + " " + lastName).trim();
                    if (!fullName.isBlank()) {
                        claimantName = fullName;
                    }
                    claimantPhone = toTrimmedString(dataMap.get("phoneNumber"));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch claimant profile snapshot: {}", ex.getMessage());
        }
        claim.setClaimantName(claimantName);
        claim.setClaimantPhone(claimantPhone);
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String resolved = String.valueOf(value).trim();
        return resolved.isBlank() ? null : resolved;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim().toLowerCase();
        return trimmed.isBlank() ? null : trimmed;
    }

    private java.time.LocalDate parseDate(String dateValue) {
        if (dateValue == null || dateValue.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(dateValue.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD.");
        }
    }

    private boolean hasSettlementBankDetails(Claim claim) {
        return toTrimmedString(claim.getBeneficiaryName()) != null
                && toTrimmedString(claim.getBankAccountNumber()) != null
                && toTrimmedString(claim.getIfscCode()) != null
                && toTrimmedString(claim.getBankName()) != null;
    }

    private ClaimBankDetailsResponse toBankDetailsResponse(Claim claim) {
        return ClaimBankDetailsResponse.builder()
                .claimId(claim.getId())
                .beneficiaryName(claim.getBeneficiaryName())
                .accountNumber(claim.getBankAccountNumber())
                .ifscCode(claim.getIfscCode())
                .bankName(claim.getBankName())
                .updatedAt(claim.getBankDetailsUpdatedAt())
                .build();
    }

    private ClaimAuditEventResponse toAuditResponse(ClaimAuditEvent event) {
        return ClaimAuditEventResponse.builder()
                .id(event.getId())
                .claimId(event.getClaimId())
                .claimNumber(event.getClaimNumber())
                .actionType(event.getActionType())
                .oldStatus(event.getOldStatus())
                .newStatus(event.getNewStatus())
                .assignedAdjusterId(event.getAssignedAdjusterId())
                .approvedAmount(event.getApprovedAmount())
                .details(event.getDetails())
                .changedBy(event.getChangedBy())
                .createdAt(event.getCreatedAt())
                .build();
    }

    private void recordAuditEvent(
            Claim claim,
            ClaimAuditEvent.AuditAction actionType,
            Claim.ClaimStatus oldStatus,
            Claim.ClaimStatus newStatus,
            String changedBy,
            String details,
            Long assignedAdjusterId,
            BigDecimal approvedAmount
    ) {
        ClaimAuditEvent event = ClaimAuditEvent.builder()
                .claimId(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .actionType(actionType)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .assignedAdjusterId(assignedAdjusterId)
                .approvedAmount(approvedAmount)
                .details(details)
                .changedBy(changedBy)
                .build();
        claimAuditEventRepository.save(event);
    }
}
