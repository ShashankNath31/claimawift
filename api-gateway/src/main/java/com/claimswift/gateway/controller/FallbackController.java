package com.claimswift.gateway.controller;

import com.claimswift.gateway.util.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback Controller for Circuit Breaker
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public ResponseEntity<ApiResponse<Void>> authFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Auth service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Authentication service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/claims")
    public ResponseEntity<ApiResponse<Void>> claimsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Claim service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Claim service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/documents")
    public ResponseEntity<ApiResponse<Void>> documentsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Document service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Document service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/assessments")
    public ResponseEntity<ApiResponse<Void>> assessmentsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Assessment service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Assessment service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/payments")
    public ResponseEntity<ApiResponse<Void>> paymentsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Payment service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Payment service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> notificationsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Notification service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Notification service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }

    @RequestMapping("/reports")
    public ResponseEntity<ApiResponse<Void>> reportsFallback(
            @RequestHeader(value = "X-Correlation-ID", required = false) String requestId) {
        log.warn("Reporting service fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Reporting service is temporarily unavailable.",
                        "SERVICE_UNAVAILABLE",
                        requestId
                ));
    }
}
