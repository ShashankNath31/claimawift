package com.claimswift.claimservice.controller;

import com.claimswift.claimservice.dto.ClaimRequest;
import com.claimswift.claimservice.dto.ClaimResponse;
import com.claimswift.claimservice.dto.ClaimStatusUpdateRequest;
import com.claimswift.claimservice.entity.Claim;
import com.claimswift.claimservice.security.JwtTokenProvider;
import com.claimswift.claimservice.service.ClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClaimController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClaimService claimService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private ClaimRequest claimRequest;
    private ClaimResponse claimResponse;
    private ClaimStatusUpdateRequest statusUpdateRequest;

    @BeforeEach
    void setUp() {
        claimRequest = new ClaimRequest();
        claimRequest.setVehicleMake("Toyota");
        claimRequest.setVehicleModel("Camry");
        claimRequest.setVehicleYear(2020);
        claimRequest.setVehicleRegistration("MH12AB1234");
        claimRequest.setPolicyNumber("POL-123456");
        claimRequest.setIncidentDescription("Test accident");
        claimRequest.setIncidentDate(LocalDate.now().minusDays(1));
        claimRequest.setIncidentLocation("Pune");
        claimRequest.setClaimAmount(BigDecimal.valueOf(5000.00));

        claimResponse = new ClaimResponse();
        claimResponse.setId(1L);
        claimResponse.setClaimNumber("CLM-TEST123");
        claimResponse.setVehicleMake("Toyota");
        claimResponse.setVehicleModel("Camry");
        claimResponse.setStatus(Claim.ClaimStatus.SUBMITTED);

        statusUpdateRequest = new ClaimStatusUpdateRequest();
        statusUpdateRequest.setStatus(Claim.ClaimStatus.UNDER_REVIEW);
        statusUpdateRequest.setAssignedAdjusterId(99L);
    }

    @Test
    void submitClaim_Success() throws Exception {
        when(claimService.submitClaim(any(), anyLong(), anyString())).thenReturn(claimResponse);

        mockMvc.perform(post("/api/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", 1L)
                .requestAttr("username", "testuser")
                .content(objectMapper.writeValueAsString(claimRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.claimNumber").value("CLM-TEST123"));
    }

    @Test
    void getClaimById_Success() throws Exception {
        when(claimService.getClaimById(anyLong(), anyLong())).thenReturn(claimResponse);

        mockMvc.perform(get("/api/claims/1")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getClaimByNumber_Success() throws Exception {
        when(claimService.getClaimByNumber(anyString(), anyLong())).thenReturn(claimResponse);

        mockMvc.perform(get("/api/claims/number/CLM-TEST123")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.claimNumber").value("CLM-TEST123"));
    }

    @Test
    void getMyClaims_Success() throws Exception {
        List<ClaimResponse> claims = Arrays.asList(claimResponse);
        when(claimService.getClaimsByClaimant(anyLong())).thenReturn(claims);

        mockMvc.perform(get("/api/claims/my-claims")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void getClaimHistory_Success() throws Exception {
        List<ClaimResponse> claims = Arrays.asList(claimResponse);
        when(claimService.getClaimHistory(anyLong())).thenReturn(claims);

        mockMvc.perform(get("/api/claims/history")
                .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void updateClaimStatus_Success() throws Exception {
        when(claimService.updateClaimStatus(anyLong(), any(), anyLong(), anyString())).thenReturn(claimResponse);

        mockMvc.perform(put("/api/claims/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("userId", 1L)
                .requestAttr("username", "testuser")
                .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Claim status updated"));
    }

    @Test
    void getClaimsByStatus_Success() throws Exception {
        List<ClaimResponse> claims = Arrays.asList(claimResponse);
        when(claimService.getClaimsByStatus("SUBMITTED")).thenReturn(claims);

        mockMvc.perform(get("/api/claims/status/SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].status").value("SUBMITTED"));
    }

    @Test
    void healthCheck_Success() throws Exception {
        mockMvc.perform(get("/api/claims/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Claim Service is running"));
    }
}
