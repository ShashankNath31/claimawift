package com.claimswift.reportingservice.controller;

import com.claimswift.reportingservice.dto.AdjusterPerformanceReport;
import com.claimswift.reportingservice.dto.ClaimSummaryReport;
import com.claimswift.reportingservice.dto.PaymentReport;
import com.claimswift.reportingservice.service.ReportingService;
import com.claimswift.reportingservice.util.StandardResponse;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Reporting Controller
 * Provides endpoints for generating various reports
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportingController {

    private final ReportingService reportingService;
    
    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/claims/summary")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADJUSTER', 'ADMIN')")
    public ResponseEntity<StandardResponse<ClaimSummaryReport>> getClaimSummaryReport(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Generating claim summary report for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        ClaimSummaryReport report = reportingService.generateClaimSummaryReport(start, end);
        return ResponseEntity.ok(StandardResponse.success("Claim summary report generated successfully", report));
    }

    @GetMapping("/claims/summary/pdf")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportClaimSummaryPdf(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Exporting claim summary report as PDF for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        byte[] pdfBytes = reportingService.exportClaimSummaryToPdf(start, end);
        
        String filename = "claim-summary-report-" + LocalDate.now() + ".pdf";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<PaymentReport>> getPaymentReport(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Generating payment report for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        PaymentReport report = reportingService.generatePaymentReport(start, end);
        return ResponseEntity.ok(StandardResponse.success("Payment report generated successfully", report));
    }

    @GetMapping("/payments/pdf")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportPaymentReportPdf(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Exporting payment report as PDF for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        byte[] pdfBytes = reportingService.exportPaymentReportToPdf(start, end);
        
        String filename = "payment-report-" + LocalDate.now() + ".pdf";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/adjusters/performance")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<AdjusterPerformanceReport>> getAdjusterPerformanceReport(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Generating adjuster performance report for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        AdjusterPerformanceReport report = reportingService.generateAdjusterPerformanceReport(start, end);
        return ResponseEntity.ok(StandardResponse.success("Adjuster performance report generated successfully", report));
    }

    @GetMapping("/adjusters/performance/pdf")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportAdjusterPerformancePdf(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Exporting adjuster performance report as PDF for period: {} to {}", startDate, endDate);
        
        String start = startDate != null ? startDate.toString() : null;
        String end = endDate != null ? endDate.toString() : null;
        
        byte[] pdfBytes = reportingService.exportAdjusterPerformanceToPdf(start, end);
        
        String filename = "adjuster-performance-report-" + LocalDate.now() + ".pdf";
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/claim-event")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardResponse<Void>> reportClaimEvent(
            @RequestBody @NotEmpty(message = "Event payload is required") Map<String, Object> eventData) {
        if (!eventData.containsKey("claimId")) {
            throw new IllegalArgumentException("claimId is required in event payload");
        }
        if (!eventData.containsKey("eventType")) {
            throw new IllegalArgumentException("eventType is required in event payload");
        }
        reportingService.recordClaimEvent(eventData);
        log.info("Claim event received: {}", eventData);
        return ResponseEntity.ok(StandardResponse.success("Claim event recorded", null));
    }

    @PostMapping("/status-change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardResponse<Void>> reportStatusChange(
            @RequestBody @NotEmpty(message = "Status payload is required") Map<String, Object> statusChangeData) {
        if (!statusChangeData.containsKey("claimId")) {
            throw new IllegalArgumentException("claimId is required in status payload");
        }
        boolean hasLegacyStatus = statusChangeData.containsKey("status");
        boolean hasWorkflowStatuses = statusChangeData.containsKey("oldStatus") && statusChangeData.containsKey("newStatus");
        if (!hasLegacyStatus && !hasWorkflowStatuses) {
            throw new IllegalArgumentException("status or oldStatus/newStatus is required in status payload");
        }
        reportingService.recordStatusChange(statusChangeData);
        log.info("Claim status change received: {}", statusChangeData);
        return ResponseEntity.ok(StandardResponse.success("Status change recorded", null));
    }
}
