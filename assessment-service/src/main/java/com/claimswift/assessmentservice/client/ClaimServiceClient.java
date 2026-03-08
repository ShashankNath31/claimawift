package com.claimswift.assessmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "claim-service")
public interface ClaimServiceClient {

    @PutMapping("/api/claims/{id}/status")
    void updateClaimStatus(@PathVariable("id") Long claimId, @RequestBody Map<String, Object> request);

    @GetMapping("/api/claims/internal/{id}")
    Map<String, Object> getClaimForWorkflow(@PathVariable("id") Long claimId);
}

