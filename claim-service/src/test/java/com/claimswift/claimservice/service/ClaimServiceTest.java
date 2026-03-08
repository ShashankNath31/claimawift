package com.claimswift.claimservice.service;

import com.claimswift.claimservice.client.AssessmentServiceClient;
import com.claimswift.claimservice.client.AuthServiceClient;
import com.claimswift.claimservice.client.NotificationServiceClient;
import com.claimswift.claimservice.client.ReportingServiceClient;
import com.claimswift.claimservice.dto.ClaimBankDetailsRequest;
import com.claimswift.claimservice.dto.ClaimBankDetailsResponse;
import com.claimswift.claimservice.dto.ClaimRequest;
import com.claimswift.claimservice.dto.ClaimResponse;
import com.claimswift.claimservice.dto.ClaimStatusUpdateRequest;
import com.claimswift.claimservice.dto.PolicyPortfolioResponse;
import com.claimswift.claimservice.entity.Claim;
import com.claimswift.claimservice.entity.ClaimAuditEvent;
import com.claimswift.claimservice.entity.Policy;
import com.claimswift.claimservice.mapper.ClaimMapper;
import com.claimswift.claimservice.repository.ClaimAuditEventRepository;
import com.claimswift.claimservice.repository.ClaimRepository;
import com.claimswift.claimservice.repository.PolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimMapper claimMapper;
    @Mock
    private AssessmentServiceClient assessmentServiceClient;
    @Mock
    private ReportingServiceClient reportingServiceClient;
    @Mock
    private AuthServiceClient authServiceClient;
    @Mock
    private NotificationServiceClient notificationServiceClient;
    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private ClaimAuditEventRepository claimAuditEventRepository;

    @InjectMocks
    private ClaimService claimService;

    private Claim claim;
    private ClaimRequest claimRequest;
    private ClaimResponse claimResponse;
    private Policy activePolicy;

    @BeforeEach
    void setUp() {
        claim = new Claim();
        claim.setId(1L);
        claim.setClaimNumber("CLM-TEST123");
        claim.setPolicyNumber("POL-123456");
        claim.setClaimantId(1L);
        claim.setClaimantName("Test User");
        claim.setClaimantPhone("9999999999");
        claim.setVehicleRegistration("MH12AB1234");
        claim.setVehicleMake("Toyota");
        claim.setVehicleModel("Camry");
        claim.setVehicleYear(2020);
        claim.setIncidentDate(LocalDate.now().minusDays(1));
        claim.setIncidentDescription("Accident damage");
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);
        claim.setClaimAmount(new BigDecimal("5000.00"));
        claim.setAdjusterId(99L);
        claim.setCreatedAt(LocalDateTime.now().minusHours(2));

        claimRequest = new ClaimRequest();
        claimRequest.setVehicleMake("Toyota");
        claimRequest.setVehicleModel("Camry");
        claimRequest.setVehicleYear(2020);
        claimRequest.setVehicleRegistration("MH12AB1234");
        claimRequest.setPolicyNumber("POL-123456");
        claimRequest.setIncidentDate(LocalDate.now().minusDays(1));
        claimRequest.setIncidentDescription("Accident damage");
        claimRequest.setIncidentLocation("Pune");
        claimRequest.setClaimAmount(new BigDecimal("5000.00"));

        claimResponse = new ClaimResponse();
        claimResponse.setId(1L);
        claimResponse.setClaimNumber("CLM-TEST123");
        claimResponse.setStatus(Claim.ClaimStatus.SUBMITTED);

        activePolicy = Policy.builder()
                .id(10L)
                .policyNumber("POL-123456")
                .policyholderId(1L)
                .policyType("Motor")
                .planName("Plan")
                .insurerName("Insurer")
                .vehicleRegistration("MH12AB1234")
                .coverageAmount(new BigDecimal("100000.00"))
                .startDate(LocalDate.now().minusDays(30))
                .expiryDate(LocalDate.now().plusDays(30))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitClaimSuccess() {
        when(policyRepository.existsByPolicyholderId(1L)).thenReturn(true);
        when(policyRepository.findByPolicyNumberAndPolicyholderId("POL-123456", 1L))
                .thenReturn(Optional.of(activePolicy));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(claimMapper.toEntity(claimRequest)).thenReturn(claim);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);
        when(authServiceClient.getCurrentUser()).thenReturn(Map.of("data", Map.of(
                "firstName", "Test",
                "lastName", "User",
                "phoneNumber", "9999999999"
        )));

        ClaimResponse result = claimService.submitClaim(claimRequest, 1L, "testuser");

        assertNotNull(result);
        verify(reportingServiceClient).reportClaimEvent(any(Map.class));
        verify(notificationServiceClient).sendClaimStatusNotification(any(Map.class));
    }

    @Test
    void submitClaimValidationScenarios() {
        when(policyRepository.existsByPolicyholderId(1L)).thenReturn(true);

        ClaimRequest missingPolicy = new ClaimRequest();
        missingPolicy.setPolicyNumber(" ");
        missingPolicy.setClaimAmount(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> claimService.submitClaim(missingPolicy, 1L, "u"));

        Policy expired = Policy.builder()
                .policyNumber("POL-123456")
                .policyholderId(1L)
                .coverageAmount(new BigDecimal("500.00"))
                .expiryDate(LocalDate.now().minusDays(1))
                .build();
        when(policyRepository.findByPolicyNumberAndPolicyholderId("POL-123456", 1L)).thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class, () -> claimService.submitClaim(claimRequest, 1L, "u"));

        when(policyRepository.findByPolicyNumberAndPolicyholderId("POL-123456", 1L)).thenReturn(Optional.of(activePolicy));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(anyString(), any()))
                .thenReturn(new BigDecimal("98000.00"));
        assertThrows(IllegalArgumentException.class, () -> claimService.submitClaim(claimRequest, 1L, "u"));
    }

    @Test
    void submitClaimFallsBackToUsernameWhenAuthSnapshotUnavailable() {
        when(policyRepository.existsByPolicyholderId(1L)).thenReturn(true);
        when(policyRepository.findByPolicyNumberAndPolicyholderId("POL-123456", 1L))
                .thenReturn(Optional.of(activePolicy));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(claimMapper.toEntity(claimRequest)).thenReturn(claim);
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);
        when(authServiceClient.getCurrentUser()).thenThrow(new RuntimeException("auth down"));

        claimService.submitClaim(claimRequest, 1L, "fallback-user");

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        assertEquals("fallback-user", captor.getValue().getClaimantName());
        assertNull(captor.getValue().getClaimantPhone());
    }

    @Test
    void getMyPoliciesBuildsPortfolioWithCoverageValues() {
        Policy expiredPolicy = Policy.builder()
                .id(11L)
                .policyNumber("POL-OLD")
                .policyholderId(1L)
                .policyType("Motor")
                .planName("Old")
                .insurerName("Insurer")
                .vehicleRegistration("DL1")
                .coverageAmount(new BigDecimal("20000.00"))
                .startDate(LocalDate.now().minusYears(1))
                .expiryDate(LocalDate.now().minusDays(2))
                .build();
        when(policyRepository.existsByPolicyholderId(1L)).thenReturn(true);
        when(policyRepository.findByPolicyholderIdOrderByExpiryDateDesc(1L)).thenReturn(List.of(activePolicy, expiredPolicy));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses("POL-123456", List.of(Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.CANCELLED)))
                .thenReturn(new BigDecimal("1000.00"));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses("POL-OLD", List.of(Claim.ClaimStatus.REJECTED, Claim.ClaimStatus.CANCELLED)))
                .thenReturn(new BigDecimal("30000.00"));

        PolicyPortfolioResponse response = claimService.getMyPolicies(1L);

        assertEquals(2L, response.getTotalPolicies());
        assertEquals(1L, response.getActivePolicies());
        assertEquals(1L, response.getExpiredPolicies());
        assertEquals(new BigDecimal("99000.00"), response.getPolicies().get(0).getAvailableCoverage());
        assertEquals(BigDecimal.ZERO, response.getPolicies().get(1).getAvailableCoverage());
    }

    @Test
    void bankDetailsFlows() {
        claim.setStatus(Claim.ClaimStatus.APPROVED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClaimBankDetailsRequest request = ClaimBankDetailsRequest.builder()
                .beneficiaryName(" John ")
                .accountNumber(" 1234567890 ")
                .ifscCode(" hdfc0123456 ")
                .bankName(" SBI ")
                .build();

        ClaimBankDetailsResponse updated = claimService.upsertClaimBankDetails(1L, request, 1L, "user");
        assertEquals("John", updated.getBeneficiaryName());
        assertEquals("HDFC0123456", updated.getIfscCode());

        ClaimBankDetailsResponse fetched = claimService.getClaimBankDetails(1L, 1L);
        assertEquals("1234567890", fetched.getAccountNumber());

        assertThrows(AccessDeniedException.class, () -> claimService.upsertClaimBankDetails(1L, request, 2L, "x"));
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);
        assertThrows(IllegalArgumentException.class, () -> claimService.upsertClaimBankDetails(1L, request, 1L, "x"));
    }

    @Test
    void getClaimBankDetailsEnforcesRoleAccess() {
        claim.setStatus(Claim.ClaimStatus.APPROVED);
        claim.setBeneficiaryName("A");
        claim.setBankAccountNumber("1");
        claim.setIfscCode("ABCD0123456");
        claim.setBankName("Bank");
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

        setRoles("ROLE_ADJUSTER");
        assertThrows(AccessDeniedException.class, () -> claimService.getClaimBankDetails(1L, 99L));

        SecurityContextHolder.clearContext();
        setRoles("ROLE_POLICYHOLDER");
        assertThrows(AccessDeniedException.class, () -> claimService.getClaimBankDetails(1L, 2L));

        claim.setBeneficiaryName(" ");
        SecurityContextHolder.clearContext();
        assertThrows(IllegalArgumentException.class, () -> claimService.getClaimBankDetails(1L, 1L));
    }

    @Test
    void claimLookupAccessControls() {
        claim.setAdjusterId(55L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.findByClaimNumber("CLM-TEST123")).thenReturn(Optional.of(claim));
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        setRoles("ROLE_POLICYHOLDER");
        assertThrows(AccessDeniedException.class, () -> claimService.getClaimById(1L, 2L));
        assertEquals(1L, claimService.getClaimById(1L, 1L).getId());

        SecurityContextHolder.clearContext();
        setRoles("ROLE_ADJUSTER");
        assertThrows(AccessDeniedException.class, () -> claimService.getClaimByNumber("CLM-TEST123", 66L));
        assertEquals(1L, claimService.getClaimByNumber("CLM-TEST123", 55L).getId());
    }

    @Test
    void getClaimAuditTrailMapsAndEnforcesAccess() {
        ClaimAuditEvent event = ClaimAuditEvent.builder()
                .id(9L)
                .claimId(1L)
                .claimNumber("CLM-TEST123")
                .actionType(ClaimAuditEvent.AuditAction.CLAIM_UPDATED)
                .oldStatus(Claim.ClaimStatus.SUBMITTED)
                .newStatus(Claim.ClaimStatus.UNDER_REVIEW)
                .changedBy("admin")
                .build();
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimAuditEventRepository.findByClaimIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(event));

        assertEquals(1, claimService.getClaimAuditTrail(1L).size());
    }

    @Test
    void updateClaimStatusApprovedSetsDefaultAmountAndNotifiesAssessment() {
        claim.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        claim.setApprovedAmount(null);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);

        ClaimStatusUpdateRequest request = new ClaimStatusUpdateRequest();
        request.setStatus(Claim.ClaimStatus.APPROVED);
        request.setNotes("approved");

        claimService.updateClaimStatus(1L, request, 1L, "manager");

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        assertEquals(new BigDecimal("5000.00"), captor.getValue().getApprovedAmount());
        verify(assessmentServiceClient).notifyAssessmentComplete(any(Map.class));
        verify(reportingServiceClient).reportStatusChange(any(Map.class));
    }

    @Test
    void updateClaimStatusAdjustedByAdjusterNotifiesActiveManagers() {
        claim.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        claim.setAdjusterId(99L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);
        when(authServiceClient.getManagers()).thenReturn(Map.of(
                "data", List.of(
                        Map.of("id", 201L, "status", "ACTIVE"),
                        Map.of("id", 202L, "status", "INACTIVE")
                )
        ));

        setRoles("ROLE_ADJUSTER");
        ClaimStatusUpdateRequest request = ClaimStatusUpdateRequest.builder()
                .status(Claim.ClaimStatus.ADJUSTED)
                .notes("need manager review")
                .build();

        claimService.updateClaimStatus(1L, request, 99L, "adjuster1");

        verify(notificationServiceClient, times(1)).sendNotification(any(Map.class));
        verify(notificationServiceClient).sendClaimStatusNotification(any(Map.class));
    }

    @Test
    void updateClaimStatusRejectsPaidByAdjusterOnlyRole() {
        claim.setStatus(Claim.ClaimStatus.APPROVED);
        claim.setAdjusterId(99L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        setRoles("ROLE_ADJUSTER");

        ClaimStatusUpdateRequest request = ClaimStatusUpdateRequest.builder()
                .status(Claim.ClaimStatus.PAID)
                .build();

        assertThrows(IllegalArgumentException.class, () -> claimService.updateClaimStatus(1L, request, 99L, "adj"));
    }

    @Test
    void updateClaimStatusEnforcesAssignmentForAdjuster() {
        claim.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        claim.setAdjusterId(300L);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        setRoles("ROLE_ADJUSTER");

        ClaimStatusUpdateRequest request = ClaimStatusUpdateRequest.builder()
                .status(Claim.ClaimStatus.REJECTED)
                .build();

        assertThrows(AccessDeniedException.class, () -> claimService.updateClaimStatus(1L, request, 301L, "adj"));
    }

    @Test
    void pagingAndSearchMethodsDelegateToRepository() {
        when(claimRepository.findByStatus(Claim.ClaimStatus.SUBMITTED)).thenReturn(List.of(claim));
        when(claimMapper.toResponseList(any())).thenReturn(List.of(claimResponse));
        when(claimRepository.findByStatus(any(), any())).thenReturn(new PageImpl<>(List.of(claim)));
        when(claimRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(claim)));
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);
        when(claimRepository.searchClaims("q")).thenReturn(List.of(claim));
        when(claimRepository.searchMyClaims(any(), any(), any(), any(), any())).thenReturn(List.of(claim));
        when(claimRepository.findByAdjusterId(99L)).thenReturn(List.of(claim));
        when(claimRepository.findAll()).thenReturn(List.of(claim));

        assertEquals(1, claimService.getClaimsByStatus("SUBMITTED").size());
        Page<ClaimResponse> statusPage = claimService.getClaimsPage(0, 10, "submitted", "id", "desc");
        assertEquals(1, statusPage.getTotalElements());
        assertEquals(1, claimService.getClaimsPage(0, 10, null, "id", "asc").getTotalElements());
        assertEquals(1, claimService.searchClaims("q").size());
        assertEquals(1, claimService.searchMyClaims(1L, "  Q ", "submitted", "2026-01-01", "2026-01-31").size());
        assertThrows(IllegalArgumentException.class, () -> claimService.searchMyClaims(1L, null, null, "bad-date", null));
        assertEquals(1, claimService.getClaimsByAdjuster(99L).size());
        assertEquals(1, claimService.getAllClaims().size());
    }

    @Test
    void updateAndDeleteClaimFlows() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);

        ClaimResponse updated = claimService.updateClaim(1L, claimRequest, "editor");
        assertEquals(1L, updated.getId());

        when(claimRepository.existsById(1L)).thenReturn(true);
        claimService.deleteClaim(1L);
        verify(claimRepository).deleteById(1L);

        when(claimRepository.existsById(2L)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> claimService.deleteClaim(2L));
    }

    @Test
    void assignAndUnassignFlows() {
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claimMapper.toResponse(any(Claim.class))).thenReturn(claimResponse);

        claimService.assignClaim(1L, 99L, "manager");
        verify(notificationServiceClient).sendNotification(any(Map.class));

        claim.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        claimService.unassignClaim(1L, "manager");
        verify(reportingServiceClient).reportStatusChange(any(Map.class));
        verify(notificationServiceClient).sendClaimStatusNotification(any(Map.class));

        claim.setStatus(Claim.ClaimStatus.APPROVED);
        claimService.unassignClaim(1L, "manager");
    }

    @Test
    void summaryAndStatisticsMethodsReturnExpectedKeys() {
        when(claimRepository.count()).thenReturn(10L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.SUBMITTED)).thenReturn(1L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.UNDER_REVIEW)).thenReturn(2L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.APPROVED)).thenReturn(3L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.REJECTED)).thenReturn(1L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.ADJUSTED)).thenReturn(1L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.PAID)).thenReturn(1L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.PAYMENT_FAILED)).thenReturn(1L);
        when(claimRepository.countByStatus(Claim.ClaimStatus.CANCELLED)).thenReturn(0L);
        when(claimRepository.sumApprovedClaims()).thenReturn(new BigDecimal("777.00"));

        Map<String, Object> stats = claimService.getClaimStatistics();
        Map<String, Object> summary = claimService.getClaimsSummary("2026-01-01", "2026-01-31");

        assertEquals(10L, stats.get("totalClaims"));
        assertEquals(1L, stats.get("paidClaims"));
        assertEquals(new BigDecimal("777.00"), stats.get("approvedAmount"));
        assertEquals("2026-01-01", summary.get("startDate"));
        assertEquals("2026-01-31", summary.get("endDate"));
    }

    @Test
    void historyAndAssignedClaimsDelegateToMapper() {
        when(claimRepository.findByClaimantId(1L)).thenReturn(List.of(claim));
        when(claimMapper.toResponseList(any())).thenReturn(List.of(claimResponse));
        when(claimRepository.findByStatusAndAdjusterId(Claim.ClaimStatus.UNDER_REVIEW, 99L)).thenReturn(List.of(claim));

        assertEquals(1, claimService.getClaimsByClaimant(1L).size());
        assertEquals(1, claimService.getClaimHistory(1L).size());
        assertEquals(1, claimService.getAssignedClaims(99L).size());
    }

    @Test
    void claimNotFoundCasesThrow() {
        when(claimRepository.findById(123L)).thenReturn(Optional.empty());
        when(claimRepository.findByClaimNumber("MISSING")).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> claimService.getClaimById(123L));
        assertThrows(RuntimeException.class, () -> claimService.getClaimByNumber("MISSING"));
    }

    private void setRoles(String... roles) {
        Set<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toSet());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", "n/a", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
