package com.claimswift.claimservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign Client for Reporting Service
 */
@FeignClient(name = "reporting-service")
public interface ReportingServiceClient {

    @PostMapping("/api/reports/claim-event")
    void reportClaimEvent(@RequestBody Map<String, Object> eventData);

    @PostMapping("/api/reports/status-change")
    void reportStatusChange(@RequestBody Map<String, Object> statusChangeData);
}