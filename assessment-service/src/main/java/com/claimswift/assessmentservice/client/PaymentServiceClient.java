package com.claimswift.assessmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @PostMapping("/api/payments/internal/auto-process")
    Map<String, Object> autoProcessPayment(@RequestBody Map<String, Object> request);
}

