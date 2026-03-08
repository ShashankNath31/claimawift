package com.claimswift.claimservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign Client for Assessment Service
 */
@FeignClient(name = "assessment-service")
public interface AssessmentServiceClient {

    @PostMapping("/api/assessments/request")
    Map<String, Object> requestAssessment(@RequestParam("claimId") Long claimId);

    @PostMapping("/api/assessments/notify-complete")
    void notifyAssessmentComplete(@RequestBody Map<String, Object> assessmentData);
}
