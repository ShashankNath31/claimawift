package com.claimswift.paymentservice.controller;

import com.claimswift.paymentservice.dto.PaymentRequest;
import com.claimswift.paymentservice.dto.PaymentResponse;
import com.claimswift.paymentservice.dto.AutoPaymentRequest;
import com.claimswift.paymentservice.entity.PaymentTransaction;
import com.claimswift.paymentservice.service.PaymentService;
import com.claimswift.paymentservice.util.StandardResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@Validated
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER') and !hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("username") String username) {

        log.info("Processing payment request for claim {} by user {}", request.getClaimId(), username);

        PaymentResponse response = paymentService.processPayment(
                request,
                userId,
                username
        );

        return ResponseEntity.ok(StandardResponse.success("Payment processed", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER') and !hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<PaymentResponse>> getPayment(
            @PathVariable("id") @Positive(message = "Payment ID must be positive") Long id,
            @RequestAttribute("userId") Long requesterUserId) {

        log.info("Fetching payment {}", id);
        PaymentResponse response = paymentService.getPayment(id, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success("Payment retrieved", response));
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasRole('MANAGER') and !hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<List<PaymentResponse>>> getPaymentsByClaim(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId,
            @RequestAttribute("userId") Long requesterUserId) {

        log.info("Fetching payments for claim {}", claimId);
        List<PaymentResponse> responses = paymentService.getPaymentsByClaim(claimId, requesterUserId);
        return ResponseEntity.ok(StandardResponse.success("Payments retrieved", responses));
    }

    @GetMapping("/internal/all")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllPaymentsForReporting() {
        return ResponseEntity.ok(paymentService.getAllPayments().stream().map(this::toMap).toList());
    }

    @GetMapping("/internal/status/{status}")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPaymentsByStatusForReporting(
            @PathVariable("status") @NotBlank(message = "Status is required") String status) {
        PaymentTransaction.PaymentStatus paymentStatus = PaymentTransaction.PaymentStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(paymentService.getPaymentsByStatus(paymentStatus).stream().map(this::toMap).toList());
    }

    @GetMapping("/internal/summary")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getPaymentSummaryForReporting(
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate) {
        return ResponseEntity.ok(paymentService.getPaymentSummary(startDate, endDate));
    }

    @GetMapping("/internal/claim/{claimId}")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPaymentsByClaimForReporting(
            @PathVariable("claimId") @Positive(message = "Claim ID must be positive") Long claimId) {
        return ResponseEntity.ok(paymentService.getPaymentsByClaim(claimId).stream().map(this::toMap).toList());
    }

    @PostMapping("/internal/auto-process")
    @PreAuthorize("hasRole('MANAGER') and !hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<PaymentResponse>> autoProcessPaymentForApprovedClaim(
            @Valid @RequestBody AutoPaymentRequest request,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("username") String username) {
        PaymentResponse response = paymentService.processWorkflowPayment(request, userId, username);
        return ResponseEntity.ok(StandardResponse.success("Workflow payment processed", response));
    }

    private Map<String, Object> toMap(PaymentResponse response) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", response.getId());
        data.put("transactionId", response.getTransactionId());
        data.put("claimId", response.getClaimId());
        data.put("policyholderId", response.getPolicyholderId());
        data.put("amount", response.getAmount() != null ? response.getAmount() : BigDecimal.ZERO);
        data.put("status", response.getStatus() != null ? response.getStatus().name() : null);
        data.put("paymentMethod", response.getPaymentMethod() != null ? response.getPaymentMethod().name() : null);
        data.put("createdAt", response.getCreatedAt());
        data.put("updatedAt", response.getUpdatedAt());
        return data;
    }
}
