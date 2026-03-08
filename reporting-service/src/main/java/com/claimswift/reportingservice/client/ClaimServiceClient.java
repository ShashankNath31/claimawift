package com.claimswift.reportingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Feign Client for Claim Service
 * Used to fetch claim data for reporting
 */
@FeignClient(name = "claim-service")
public interface ClaimServiceClient {

    @GetMapping("/api/claims/internal/all")
    List<Map<String, Object>> getAllClaims();

    @GetMapping("/api/claims/internal/status/{status}")
    List<Map<String, Object>> getClaimsByStatus(@PathVariable("status") String status);

    @GetMapping("/api/claims/internal/adjuster/{adjusterId}")
    List<Map<String, Object>> getClaimsByAdjuster(@PathVariable("adjusterId") Long adjusterId);

    @GetMapping("/api/claims/internal/summary")
    Map<String, Object> getClaimsSummary(@RequestParam("startDate") String startDate, 
                                          @RequestParam("endDate") String endDate);
}
