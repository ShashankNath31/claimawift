package com.claimswift.documentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "claim-service", configuration = com.claimswift.documentservice.config.FeignConfig.class)
public interface ClaimServiceClient {

    @GetMapping("/api/claims/{id}")
    Map<String, Object> getClaimById(@PathVariable("id") Long claimId);
}
