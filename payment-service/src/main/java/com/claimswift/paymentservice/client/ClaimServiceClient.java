package com.claimswift.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign Client for Claim Service
 * Used to update claim status after payment processing
 */
@FeignClient(name = "claim-service")
public interface ClaimServiceClient {

    @GetMapping("/api/claims/internal/{id}")
    Map<String, Object> getClaimByIdForWorkflow(@PathVariable("id") Long id);

    @GetMapping("/api/claims/{id}")
    Map<String, Object> getClaimById(@PathVariable("id") Long id);

    @PutMapping("/api/claims/{id}/status")
    Map<String, Object> updateClaimStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> statusUpdate);
}
