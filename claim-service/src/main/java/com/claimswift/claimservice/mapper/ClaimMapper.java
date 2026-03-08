package com.claimswift.claimservice.mapper;

import com.claimswift.claimservice.dto.ClaimRequest;
import com.claimswift.claimservice.dto.ClaimResponse;
import com.claimswift.claimservice.entity.Claim;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ClaimMapper {

    public Claim toEntity(ClaimRequest request) {
        if (request == null) {
            return null;
        }

        Claim claim = new Claim();
        claim.setPolicyNumber(request.getPolicyNumber());
        claim.setVehicleRegistration(request.getVehicleRegistration());
        claim.setVehicleMake(request.getVehicleMake());
        claim.setVehicleModel(request.getVehicleModel());
        claim.setVehicleYear(request.getVehicleYear());
        claim.setIncidentDate(request.getIncidentDate());
        claim.setIncidentLocation(request.getIncidentLocation());
        claim.setIncidentDescription(request.getIncidentDescription());
        claim.setClaimAmount(request.getClaimAmount());
        return claim;
    }

    public ClaimResponse toResponse(Claim claim) {
        if (claim == null) {
            return null;
        }

        ClaimResponse response = new ClaimResponse();
        response.setId(claim.getId());
        response.setClaimNumber(claim.getClaimNumber());
        response.setPolicyNumber(claim.getPolicyNumber());
        response.setPolicyholderId(claim.getClaimantId());
        response.setPolicyholderName(claim.getClaimantName());
        response.setPolicyholderPhone(claim.getClaimantPhone());
        response.setVehicleRegistrationNumber(claim.getVehicleRegistration());
        response.setVehicleMake(claim.getVehicleMake());
        response.setVehicleModel(claim.getVehicleModel());
        response.setVehicleYear(claim.getVehicleYear());
        response.setIncidentDate(claim.getIncidentDate());
        response.setIncidentLocation(claim.getIncidentLocation());
        response.setIncidentDescription(claim.getIncidentDescription());
        response.setDescription(claim.getIncidentDescription());
        response.setClaimAmount(claim.getClaimAmount());
        response.setApprovedAmount(claim.getApprovedAmount());
        response.setStatus(claim.getStatus());
        response.setAssignedAdjusterId(claim.getAdjusterId());
        response.setCreatedAt(claim.getCreatedAt());
        response.setUpdatedAt(claim.getUpdatedAt());
        response.setSubmittedAt(claim.getCreatedAt());
        return response;
    }

    public void updateEntityFromRequest(ClaimRequest request, Claim claim) {
        if (request == null || claim == null) {
            return;
        }

        claim.setPolicyNumber(request.getPolicyNumber());
        claim.setVehicleRegistration(request.getVehicleRegistration());
        claim.setVehicleMake(request.getVehicleMake());
        claim.setVehicleModel(request.getVehicleModel());
        claim.setVehicleYear(request.getVehicleYear());
        claim.setIncidentDate(request.getIncidentDate());
        claim.setIncidentLocation(request.getIncidentLocation());
        claim.setIncidentDescription(request.getIncidentDescription());
        claim.setClaimAmount(request.getClaimAmount());
    }

    public List<ClaimResponse> toResponseList(List<Claim> claims) {
        return claims.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
