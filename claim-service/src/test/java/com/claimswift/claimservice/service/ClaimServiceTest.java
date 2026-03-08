package com.claimswift.claimservice.service;

import com.claimswift.claimservice.client.AssessmentServiceClient;
import com.claimswift.claimservice.client.AuthServiceClient;
import com.claimswift.claimservice.client.NotificationServiceClient;
import com.claimswift.claimservice.client.ReportingServiceClient;
import com.claimswift.claimservice.dto.ClaimRequest;
import com.claimswift.claimservice.dto.ClaimResponse;
import com.claimswift.claimservice.dto.ClaimStatusUpdateRequest;
import com.claimswift.claimservice.entity.Claim;
import com.claimswift.claimservice.entity.Policy;
import com.claimswift.claimservice.mapper.ClaimMapper;
import com.claimswift.claimservice.repository.ClaimAuditEventRepository;
import com.claimswift.claimservice.repository.ClaimRepository;
import com.claimswift.claimservice.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private Policy policy;
    private final Long claimantId = 1L;
    private final String username = "testuser";

    @BeforeEach
    void setUp() {
        claim = new Claim();
        claim.setId(1L);
        claim.setClaimNumber("CLM-TEST123");
        claim.setClaimantId(claimantId);
        claim.setVehicleMake("Toyota");
        claim.setVehicleModel("Camry");
        claim.setVehicleYear(2020);
        claim.setVehicleRegistration("MH12AB1234");
        claim.setPolicyNumber("POL-123456");
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);
        claim.setClaimAmount(BigDecimal.valueOf(5000.00));

        claimRequest = new ClaimRequest();
        claimRequest.setVehicleMake("Toyota");
        claimRequest.setVehicleModel("Camry");
        claimRequest.setVehicleYear(2020);
        claimRequest.setVehicleRegistration("MH12AB1234");
        claimRequest.setPolicyNumber("POL-123456");
        claimRequest.setIncidentDate(LocalDate.now().minusDays(1));
        claimRequest.setIncidentDescription("Accident damage");
        claimRequest.setIncidentLocation("Pune");
        claimRequest.setClaimAmount(BigDecimal.valueOf(5000.00));

        claimResponse = new ClaimResponse();
        claimResponse.setId(1L);
        claimResponse.setClaimNumber("CLM-TEST123");
        claimResponse.setVehicleMake("Toyota");
        claimResponse.setVehicleModel("Camry");
        claimResponse.setStatus(Claim.ClaimStatus.SUBMITTED);

        policy = Policy.builder()
                .id(10L)
                .policyNumber("POL-123456")
                .policyholderId(claimantId)
                .vehicleRegistration("MH12AB1234")
                .coverageAmount(BigDecimal.valueOf(100000))
                .startDate(LocalDate.now().minusDays(30))
                .expiryDate(LocalDate.now().plusDays(30))
                .build();
    }

    @Test
    void submitClaim_Success() {
        when(policyRepository.existsByPolicyholderId(claimantId)).thenReturn(true);
        when(policyRepository.findByPolicyNumberAndPolicyholderId("POL-123456", claimantId))
                .thenReturn(Optional.of(policy));
        when(claimRepository.sumClaimAmountByPolicyNumberExcludingStatuses(anyString(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(claimMapper.toEntity(claimRequest)).thenReturn(claim);
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        ClaimResponse result = claimService.submitClaim(claimRequest, claimantId, username);

        assertNotNull(result);
        assertEquals(claimResponse.getId(), result.getId());
        verify(claimRepository, times(1)).save(any(Claim.class));
    }

    @Test
    void getClaimById_Success() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        ClaimResponse result = claimService.getClaimById(1L);

        assertNotNull(result);
        assertEquals(claimResponse.getId(), result.getId());
    }

    @Test
    void getClaimById_NotFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> claimService.getClaimById(99L));
    }

    @Test
    void getClaimByNumber_Success() {
        when(claimRepository.findByClaimNumber("CLM-TEST123")).thenReturn(Optional.of(claim));
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        ClaimResponse result = claimService.getClaimByNumber("CLM-TEST123");

        assertNotNull(result);
        assertEquals(claimResponse.getId(), result.getId());
    }

    @Test
    void getClaimsByClaimant_Success() {
        List<Claim> claims = Arrays.asList(claim);
        when(claimRepository.findByClaimantId(claimantId)).thenReturn(claims);
        when(claimMapper.toResponseList(claims)).thenReturn(Arrays.asList(claimResponse));

        List<ClaimResponse> results = claimService.getClaimsByClaimant(claimantId);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void updateClaimStatus_Success() {
        ClaimStatusUpdateRequest updateRequest = new ClaimStatusUpdateRequest();
        updateRequest.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        updateRequest.setAssignedAdjusterId(99L);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        ClaimResponse result = claimService.updateClaimStatus(1L, updateRequest, claimantId, username);

        assertNotNull(result);
        verify(claimRepository, times(1)).save(any(Claim.class));
    }

    @Test
    void getClaimsByStatus_Success() {
        List<Claim> claims = Arrays.asList(claim);
        when(claimRepository.findByStatus(Claim.ClaimStatus.SUBMITTED)).thenReturn(claims);
        when(claimMapper.toResponseList(claims)).thenReturn(Arrays.asList(claimResponse));

        List<ClaimResponse> results = claimService.getClaimsByStatus("SUBMITTED");

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void updateClaimStatus_ApprovedWithoutAmount_SetsClaimAmountAsApprovedAmount() {
        claim.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        claim.setApprovedAmount(null);

        ClaimStatusUpdateRequest updateRequest = new ClaimStatusUpdateRequest();
        updateRequest.setStatus(Claim.ClaimStatus.APPROVED);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        claimService.updateClaimStatus(1L, updateRequest, claimantId, username);

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        assertEquals(BigDecimal.valueOf(5000.00), captor.getValue().getApprovedAmount());
    }

    @Test
    void updateClaimStatus_PaidWithExplicitAmount_UsesRequestedApprovedAmount() {
        claim.setStatus(Claim.ClaimStatus.APPROVED);
        claim.setApprovedAmount(null);

        ClaimStatusUpdateRequest updateRequest = new ClaimStatusUpdateRequest();
        updateRequest.setStatus(Claim.ClaimStatus.PAID);
        updateRequest.setApprovedAmount(BigDecimal.valueOf(4200.00));

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(claimRepository.save(any(Claim.class))).thenReturn(claim);
        when(claimMapper.toResponse(claim)).thenReturn(claimResponse);

        claimService.updateClaimStatus(1L, updateRequest, claimantId, username);

        ArgumentCaptor<Claim> captor = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(captor.capture());
        assertEquals(BigDecimal.valueOf(4200.00), captor.getValue().getApprovedAmount());
    }
}
