package com.claimswift.assessmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "document-service")
public interface DocumentServiceClient {

    @GetMapping("/api/documents/internal/claim/{claimId}/count")
    Map<String, Object> getClaimDocumentCount(@PathVariable("claimId") Long claimId);
}

