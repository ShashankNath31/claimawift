package com.claimswift.reportingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Feign Client for Payment Service
 * Used to fetch payment data for reporting
 */
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @GetMapping("/api/payments/internal/all")
    List<Map<String, Object>> getAllPayments();

    @GetMapping("/api/payments/internal/status/{status}")
    List<Map<String, Object>> getPaymentsByStatus(@PathVariable("status") String status);

    @GetMapping("/api/payments/internal/summary")
    Map<String, Object> getPaymentsSummary(@RequestParam("startDate") String startDate, 
                                            @RequestParam("endDate") String endDate);

    @GetMapping("/api/payments/internal/claim/{claimId}")
    List<Map<String, Object>> getPaymentsByClaim(@PathVariable("claimId") Long claimId);
}
