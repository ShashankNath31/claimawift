package com.claimswift.reportingservice.service;

import com.claimswift.reportingservice.client.ClaimServiceClient;
import com.claimswift.reportingservice.client.PaymentServiceClient;
import com.claimswift.reportingservice.dto.AdjusterPerformanceReport;
import com.claimswift.reportingservice.dto.ClaimSummaryReport;
import com.claimswift.reportingservice.dto.PaymentReport;
import com.claimswift.reportingservice.entity.ReportEvent;
import com.claimswift.reportingservice.repository.ReportEventRepository;
import com.claimswift.reportingservice.util.PdfGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private ClaimServiceClient claimServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private PdfGenerator pdfGenerator;
    @Mock
    private ReportEventRepository reportEventRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReportingService reportingService;

    @Test
    void generateClaimSummaryReportCalculatesTotals() {
        List<Map<String, Object>> claims = List.of(
                Map.of(
                        "id", 1L,
                        "status", "APPROVED",
                        "claimAmount", new BigDecimal("1000.00"),
                        "approvedAmount", new BigDecimal("800.00"),
                        "createdAt", LocalDateTime.now().toString()
                ),
                Map.of(
                        "id", 2L,
                        "status", "REJECTED",
                        "claimAmount", new BigDecimal("400.00"),
                        "createdAt", LocalDateTime.now().toString()
                )
        );
        when(claimServiceClient.getAllClaims()).thenReturn(claims);

        ClaimSummaryReport report = reportingService.generateClaimSummaryReport(null, null);

        assertEquals(2L, report.getTotalClaims());
        assertEquals(1L, report.getApprovedClaims());
        assertEquals(1L, report.getRejectedClaims());
        assertEquals(new BigDecimal("1400.00"), report.getTotalClaimAmount());
        assertEquals("All Time", report.getReportPeriod());
    }

    @Test
    void generatePaymentReportCalculatesBuckets() {
        List<Map<String, Object>> payments = List.of(
                Map.of("status", "APPROVED", "amount", new BigDecimal("900.00"), "paymentMethod", "UPI", "createdAt", LocalDateTime.now().toString()),
                Map.of("status", "REJECTED", "amount", new BigDecimal("100.00"), "paymentMethod", "NEFT", "createdAt", LocalDateTime.now().toString()),
                Map.of("status", "PROCESSING", "amount", new BigDecimal("50.00"), "paymentMethod", "UPI", "createdAt", LocalDateTime.now().toString())
        );
        when(paymentServiceClient.getAllPayments()).thenReturn(payments);

        PaymentReport report = reportingService.generatePaymentReport(null, null);

        assertEquals(3L, report.getTotalPayments());
        assertEquals(1L, report.getApprovedPayments());
        assertEquals(1L, report.getRejectedPayments());
        assertEquals(1L, report.getPendingPayments());
        assertEquals(new BigDecimal("1050.00"), report.getTotalAmount());
    }

    @Test
    void generateAdjusterPerformanceReportBuildsPerAdjusterMetrics() {
        List<Map<String, Object>> claims = List.of(
                Map.of(
                        "id", 1L,
                        "adjusterId", 77L,
                        "adjusterName", "A One",
                        "status", "APPROVED",
                        "claimAmount", new BigDecimal("1000.00"),
                        "approvedAmount", new BigDecimal("800.00"),
                        "createdAt", LocalDateTime.now().minusDays(4).toString(),
                        "submittedAt", LocalDateTime.now().minusDays(4).toString(),
                        "updatedAt", LocalDateTime.now().minusDays(1).toString()
                ),
                Map.of(
                        "id", 2L,
                        "adjusterId", 77L,
                        "status", "REJECTED",
                        "claimAmount", new BigDecimal("200.00"),
                        "createdAt", LocalDateTime.now().minusDays(5).toString(),
                        "submittedAt", LocalDateTime.now().minusDays(5).toString(),
                        "updatedAt", LocalDateTime.now().minusDays(2).toString()
                )
        );
        when(claimServiceClient.getAllClaims()).thenReturn(claims);

        AdjusterPerformanceReport report = reportingService.generateAdjusterPerformanceReport(null, null);

        assertEquals(1L, report.getTotalAdjusters());
        assertEquals(2L, report.getTotalClaimsProcessed());
        assertEquals(new BigDecimal("1200.00"), report.getTotalAmountProcessed());
        assertEquals(1, report.getAdjusterPerformances().size());
    }

    @Test
    void exportMethodsDelegateToPdfGenerator() {
        when(claimServiceClient.getAllClaims()).thenReturn(List.of());
        when(paymentServiceClient.getAllPayments()).thenReturn(List.of());
        when(pdfGenerator.generateClaimSummaryPdf(any())).thenReturn(new byte[]{1});
        when(pdfGenerator.generatePaymentReportPdf(any())).thenReturn(new byte[]{2});
        when(pdfGenerator.generateAdjusterPerformancePdf(any())).thenReturn(new byte[]{3});

        assertArrayEquals(new byte[]{1}, reportingService.exportClaimSummaryToPdf(null, null));
        assertArrayEquals(new byte[]{2}, reportingService.exportPaymentReportToPdf(null, null));
        assertArrayEquals(new byte[]{3}, reportingService.exportAdjusterPerformanceToPdf(null, null));
    }

    @Test
    void recordClaimEventPersistsSerializedPayload() {
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        reportingService.recordClaimEvent(Map.of(
                "claimId", 10L,
                "claimNumber", "CLM-10",
                "eventType", "CLAIM_SUBMITTED",
                "timestamp", LocalDateTime.now().toString()
        ));

        ArgumentCaptor<ReportEvent> captor = ArgumentCaptor.forClass(ReportEvent.class);
        verify(reportEventRepository).save(captor.capture());
        assertEquals("CLAIM_EVENT", captor.getValue().getEventCategory());
        assertEquals("CLM-10", captor.getValue().getClaimNumber());
        assertEquals("CLAIM_SUBMITTED", captor.getValue().getEventType());
        assertNotNull(captor.getValue().getPayloadJson());
    }

    @Test
    void recordStatusChangeBuildsTransitionTypeAndHandlesJsonFailure() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") { });

        reportingService.recordStatusChange(Map.of(
                "claimId", 20L,
                "claimNumber", "CLM-20",
                "oldStatus", "UNDER_REVIEW",
                "newStatus", "APPROVED",
                "timestamp", LocalDateTime.now().toString()
        ));

        ArgumentCaptor<ReportEvent> captor = ArgumentCaptor.forClass(ReportEvent.class);
        verify(reportEventRepository).save(captor.capture());
        assertEquals("STATUS_CHANGE", captor.getValue().getEventCategory());
        assertEquals("UNDER_REVIEW->APPROVED", captor.getValue().getEventType());
        assertEquals("{}", captor.getValue().getPayloadJson());
    }

    @Test
    void generateReportsRejectInvalidDates() {
        assertThrows(IllegalArgumentException.class, () -> reportingService.generateClaimSummaryReport("2026/01/01", null));
        assertThrows(IllegalArgumentException.class, () -> reportingService.generatePaymentReport(null, "not-a-date"));
    }

    @Test
    void privateDateAndFormattingHelpersHandleMultipleInputShapes() {
        LocalDate parsedDate = ReflectionTestUtils.invokeMethod(reportingService, "parseFlexibleDate", "2026-01-10");
        LocalDate listDate = ReflectionTestUtils.invokeMethod(reportingService, "parseFlexibleDate", List.of(2026, 2, 5));
        LocalDate dateFromDateTime = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDate",
                LocalDateTime.of(2026, 3, 8, 10, 0)
        );
        LocalDateTime fromInstant = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDateTime",
                Instant.now()
        );
        LocalDateTime fromEpoch = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDateTime",
                System.currentTimeMillis()
        );
        LocalDateTime fromOffset = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDateTime",
                "2026-03-08T10:15:30+05:30"
        );
        LocalDateTime fromList = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDateTime",
                List.of(2026, 3, 8, 11, 22, 33, 123000000)
        );
        LocalDateTime fromMap = ReflectionTestUtils.invokeMethod(
                reportingService,
                "parseFlexibleDateTime",
                Map.of("year", 2026, "monthValue", 3, "dayOfMonth", 8, "hour", 9, "minute", 30)
        );

        String fromStart = ReflectionTestUtils.invokeMethod(reportingService, "buildPeriod", "2026-01-01", null);
        String upToEnd = ReflectionTestUtils.invokeMethod(reportingService, "buildPeriod", null, "2026-01-31");
        BigDecimal invalidDecimal = ReflectionTestUtils.invokeMethod(reportingService, "toBigDecimal", "not-decimal");
        Long invalidLong = ReflectionTestUtils.invokeMethod(reportingService, "toLong", "x");

        assertEquals(LocalDate.of(2026, 1, 10), parsedDate);
        assertEquals(LocalDate.of(2026, 2, 5), listDate);
        assertNotNull(dateFromDateTime);
        assertNotNull(fromInstant);
        assertNotNull(fromEpoch);
        assertNotNull(fromOffset);
        assertNotNull(fromList);
        assertNotNull(fromMap);
        assertEquals("From 2026-01-01", fromStart);
        assertEquals("Up to 2026-01-31", upToEnd);
        assertEquals(BigDecimal.ZERO, invalidDecimal);
        assertEquals(null, invalidLong);
    }
}
