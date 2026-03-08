package com.claimswift.reportingservice.service;

import com.claimswift.reportingservice.client.ClaimServiceClient;
import com.claimswift.reportingservice.client.PaymentServiceClient;
import com.claimswift.reportingservice.dto.ClaimSummaryReport;
import com.claimswift.reportingservice.dto.PaymentReport;
import com.claimswift.reportingservice.util.PdfGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private ClaimServiceClient claimServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private PdfGenerator pdfGenerator;

    @InjectMocks
    private ReportingService reportingService;

    private List<Map<String, Object>> mockClaims;
    private List<Map<String, Object>> mockPayments;

    @BeforeEach
    void setUp() {
        mockClaims = new ArrayList<>();
        mockPayments = new ArrayList<>();
    }

    @Test
    void generateClaimSummaryReport_Success() {
        // Given
        Map<String, Object> claim1 = new HashMap<>();
        claim1.put("id", 1L);
        claim1.put("status", "APPROVED");
        claim1.put("claimAmount", new BigDecimal("5000.00"));
        claim1.put("createdAt", LocalDateTime.now().toString());

        Map<String, Object> claim2 = new HashMap<>();
        claim2.put("id", 2L);
        claim2.put("status", "REJECTED");
        claim2.put("claimAmount", new BigDecimal("3000.00"));
        claim2.put("createdAt", LocalDateTime.now().toString());

        mockClaims.add(claim1);
        mockClaims.add(claim2);

        when(claimServiceClient.getAllClaims()).thenReturn(mockClaims);

        // When
        ClaimSummaryReport report = reportingService.generateClaimSummaryReport(null, null);

        // Then
        assertNotNull(report);
        assertEquals(2, report.getTotalClaims());
        assertEquals(1, report.getApprovedClaims());
        assertEquals(1, report.getRejectedClaims());
    }

    @Test
    void generatePaymentReport_Success() {
        // Given
        Map<String, Object> payment1 = new HashMap<>();
        payment1.put("id", 1L);
        payment1.put("status", "COMPLETED");
        payment1.put("amount", new BigDecimal("5000.00"));
        payment1.put("createdAt", LocalDateTime.now().toString());

        mockPayments.add(payment1);

        when(paymentServiceClient.getAllPayments()).thenReturn(mockPayments);

        // When
        PaymentReport report = reportingService.generatePaymentReport(null, null);

        // Then
        assertNotNull(report);
        assertEquals(1, report.getTotalPayments());
    }

    @Test
    void generateClaimSummaryReport_NoClaims() {
        // Given
        when(claimServiceClient.getAllClaims()).thenReturn(new ArrayList<>());

        // When
        ClaimSummaryReport report = reportingService.generateClaimSummaryReport(null, null);

        // Then
        assertNotNull(report);
        assertEquals(0, report.getTotalClaims());
        assertEquals(BigDecimal.ZERO, report.getTotalClaimAmount());
    }

    @Test
    void exportClaimSummaryToPdf_Success() {
        // Given
        byte[] expectedPdf = new byte[]{1, 2, 3};
        when(claimServiceClient.getAllClaims()).thenReturn(new ArrayList<>());
        when(pdfGenerator.generateClaimSummaryPdf(any())).thenReturn(expectedPdf);

        // When
        byte[] result = reportingService.exportClaimSummaryToPdf(null, null);

        // Then
        assertNotNull(result);
        assertArrayEquals(expectedPdf, result);
    }
}