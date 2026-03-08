package com.claimswift.paymentservice.service;

import com.claimswift.paymentservice.client.ClaimServiceClient;
import com.claimswift.paymentservice.client.NotificationServiceClient;
import com.claimswift.paymentservice.dto.AutoPaymentRequest;
import com.claimswift.paymentservice.dto.PaymentRequest;
import com.claimswift.paymentservice.dto.PaymentResponse;
import com.claimswift.paymentservice.entity.PaymentTransaction;
import com.claimswift.paymentservice.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentRepository;
    @Mock
    private ClaimServiceClient claimServiceClient;
    @Mock
    private NotificationServiceClient notificationServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processPaymentReturnsExistingApprovedTransaction() {
        PaymentRequest request = baseRequest();
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(10L)
                .transactionId("TXNEXIST")
                .claimId(request.getClaimId())
                .policyholderId(request.getPolicyholderId())
                .amount(request.getAmount())
                .status(PaymentTransaction.PaymentStatus.APPROVED)
                .build();

        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId())).thenReturn(approvedClaimSnapshot());
        when(paymentRepository.findFirstByClaimIdAndStatus(
                request.getClaimId(),
                PaymentTransaction.PaymentStatus.APPROVED
        )).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.processPayment(request, 99L, "operator");

        assertEquals("Payment already processed for this claim", response.getMessage());
        assertEquals(PaymentTransaction.PaymentStatus.APPROVED, response.getStatus());
        verify(paymentRepository, never()).save(any(PaymentTransaction.class));
    }

    @Test
    void processPaymentForcedSuccessUpdatesClaimAndSendsNotification() {
        PaymentRequest request = baseRequest();
        request.setForceSimulateSuccess(true);
        request.setForceSimulateFailure(false);

        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId())).thenReturn(approvedClaimSnapshot());
        when(paymentRepository.findFirstByClaimIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(1L);
            }
            return transaction;
        });

        PaymentResponse response = paymentService.processPayment(request, 5L, "tester");

        assertNotNull(response.getTransactionId());
        assertEquals(PaymentTransaction.PaymentStatus.APPROVED, response.getStatus());

        ArgumentCaptor<PaymentTransaction> saveCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentRepository, times(2)).save(saveCaptor.capture());
        assertEquals("****3456", saveCaptor.getAllValues().get(0).getAccountNumber());

        verify(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
        verify(notificationServiceClient).sendPaymentProcessedNotification(any(Map.class));
    }

    @Test
    void processPaymentForcedFailureMarksClaimPaymentFailed() {
        PaymentRequest request = baseRequest();
        request.setForceSimulateSuccess(false);
        request.setForceSimulateFailure(true);
        request.setSimulateFailureReason("bank timeout");

        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId())).thenReturn(approvedClaimSnapshot());
        when(paymentRepository.findFirstByClaimIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(2L);
            }
            return transaction;
        });

        PaymentResponse response = paymentService.processPayment(request, 5L, "tester");

        assertEquals(PaymentTransaction.PaymentStatus.REJECTED, response.getStatus());
        assertEquals("bank timeout", response.getFailureReason());
        verify(claimServiceClient).updateClaimStatus(anyLong(), any(Map.class));
        verify(notificationServiceClient, never()).sendPaymentProcessedNotification(any(Map.class));
    }

    @Test
    void processWorkflowPaymentBuildsDefaultFields() {
        AutoPaymentRequest request = new AutoPaymentRequest();
        request.setClaimId(200L);
        request.setPolicyholderId(88L);
        request.setClaimNumber("CLM-200");
        request.setAmount(new BigDecimal("500.00"));
        request.setForceSuccess(true);

        when(claimServiceClient.getClaimByIdForWorkflow(200L)).thenReturn(Map.of(
                "status", "APPROVED",
                "approvedAmount", new BigDecimal("1000.00"),
                "policyholderId", 88L,
                "claimNumber", "CLM-200"
        ));
        when(paymentRepository.findFirstByClaimIdAndStatus(200L, PaymentTransaction.PaymentStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(3L);
            }
            return transaction;
        });

        PaymentResponse response = paymentService.processWorkflowPayment(request, 7L, "workflow");

        assertEquals(PaymentTransaction.PaymentMethod.BANK_TRANSFER, response.getPaymentMethod());
        assertEquals(PaymentTransaction.PaymentStatus.APPROVED, response.getStatus());
    }

    @Test
    void processPaymentThrowsForNonApprovedClaim() {
        PaymentRequest request = baseRequest();
        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId()))
                .thenReturn(Map.of("status", "UNDER_REVIEW", "approvedAmount", BigDecimal.TEN));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> paymentService.processPayment(request, 1L, "user")
        );
        assertEquals("Settlement is allowed only for APPROVED claims.", ex.getMessage());
    }

    @Test
    void processPaymentThrowsForPolicyholderMismatch() {
        PaymentRequest request = baseRequest();
        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId()))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "approvedAmount", new BigDecimal("1000"),
                        "policyholderId", 999L
                ));

        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(request, 1L, "user"));
    }

    @Test
    void getPaymentEnforcesPolicyholderOwnership() {
        setPolicyholderOnlyAuth();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(14L)
                .policyholderId(44L)
                .claimId(10L)
                .amount(new BigDecimal("200"))
                .status(PaymentTransaction.PaymentStatus.APPROVED)
                .transactionId("TXN14")
                .build();
        when(paymentRepository.findById(14L)).thenReturn(Optional.of(transaction));

        assertThrows(AccessDeniedException.class, () -> paymentService.getPayment(14L, 77L));
    }

    @Test
    void getPaymentsByClaimUsesClaimOwnershipWhenNoTransactions() {
        setPolicyholderOnlyAuth();
        when(paymentRepository.findByClaimId(55L)).thenReturn(List.of());
        when(claimServiceClient.getClaimById(55L)).thenReturn(Map.of("data", Map.of("policyholderId", 99L)));

        List<PaymentResponse> responses = paymentService.getPaymentsByClaim(55L, 99L);
        assertEquals(0, responses.size());

        when(claimServiceClient.getClaimById(56L)).thenReturn(Map.of("data", Map.of("policyholderId", 1L)));
        when(paymentRepository.findByClaimId(56L)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> paymentService.getPaymentsByClaim(56L, 99L));
    }

    @Test
    void paymentSummaryAggregatesStatusesAndAmounts() {
        List<PaymentTransaction> transactions = List.of(
                txnWithStatus(PaymentTransaction.PaymentStatus.APPROVED, "100.00"),
                txnWithStatus(PaymentTransaction.PaymentStatus.REJECTED, "40.00"),
                txnWithStatus(PaymentTransaction.PaymentStatus.PROCESSING, "10.00")
        );
        when(paymentRepository.findAll()).thenReturn(transactions);

        Map<String, Object> summary = paymentService.getPaymentSummary("2026-01-01", "2026-01-31");

        assertEquals(3L, summary.get("totalPayments"));
        assertEquals(1L, summary.get("approvedPayments"));
        assertEquals(1L, summary.get("rejectedPayments"));
        assertEquals(1L, summary.get("pendingPayments"));
        assertEquals(new BigDecimal("150.00"), summary.get("totalAmount"));
        assertFalse(summary.isEmpty());
    }

    @Test
    void listAndLookupMethodsMapRepositoryData() {
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(21L)
                .transactionId("TXN21")
                .claimId(100L)
                .policyholderId(42L)
                .status(PaymentTransaction.PaymentStatus.APPROVED)
                .amount(new BigDecimal("11.00"))
                .build();
        when(paymentRepository.findById(21L)).thenReturn(Optional.of(txn));
        when(paymentRepository.findByClaimId(100L)).thenReturn(List.of(txn));
        when(paymentRepository.findAll()).thenReturn(List.of(txn));
        when(paymentRepository.findByStatus(PaymentTransaction.PaymentStatus.APPROVED)).thenReturn(List.of(txn));

        assertEquals(21L, paymentService.getPayment(21L).getId());
        assertEquals(1, paymentService.getPaymentsByClaim(100L).size());
        assertEquals(1, paymentService.getAllPayments().size());
        assertEquals(1, paymentService.getPaymentsByStatus(PaymentTransaction.PaymentStatus.APPROVED).size());
    }

    @Test
    void processPaymentValidationCoversAmountAndClaimNumberBranches() {
        PaymentRequest request = baseRequest();
        request.setClaimNumber(" ");
        when(claimServiceClient.getClaimByIdForWorkflow(request.getClaimId()))
                .thenReturn(Map.of(
                        "status", "APPROVED",
                        "approvedAmount", new BigDecimal("800.00"),
                        "policyholderId", 42L,
                        "claimNumber", "CLM-100"
                ));
        when(paymentRepository.findFirstByClaimIdAndStatus(anyLong(), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(90L);
            }
            return transaction;
        });

        PaymentResponse response = paymentService.processPayment(request, 1L, "user");
        assertNotNull(response.getTransactionId());

        PaymentRequest tooHigh = baseRequest();
        tooHigh.setAmount(new BigDecimal("1001.00"));
        when(claimServiceClient.getClaimByIdForWorkflow(tooHigh.getClaimId()))
                .thenReturn(approvedClaimSnapshot());
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(tooHigh, 1L, "u"));

        PaymentRequest zeroApproved = baseRequest();
        when(claimServiceClient.getClaimByIdForWorkflow(zeroApproved.getClaimId()))
                .thenReturn(Map.of("status", "APPROVED", "approvedAmount", BigDecimal.ZERO, "policyholderId", 42L));
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(zeroApproved, 1L, "u"));
    }

    @Test
    void policyholderReadValidationForNonEmptyTransactions() {
        setPolicyholderOnlyAuth();
        PaymentTransaction txn = PaymentTransaction.builder()
                .id(31L)
                .claimId(500L)
                .policyholderId(1L)
                .amount(new BigDecimal("20"))
                .status(PaymentTransaction.PaymentStatus.APPROVED)
                .build();
        when(paymentRepository.findByClaimId(500L)).thenReturn(List.of(txn));

        assertThrows(AccessDeniedException.class, () -> paymentService.getPaymentsByClaim(500L, 2L));
    }

    @Test
    void privateHelpersCoverParsingAndMaskingBranches() {
        String randomFailure = ReflectionTestUtils.invokeMethod(paymentService, "getRandomFailureReason");
        BigDecimal zeroAmount = ReflectionTestUtils.invokeMethod(paymentService, "parseAmount", (Object) null);
        BigDecimal parsedAmount = ReflectionTestUtils.invokeMethod(paymentService, "parseAmount", "15.25");
        Long parsedLong = ReflectionTestUtils.invokeMethod(paymentService, "parseLong", "12");
        Long invalidLong = ReflectionTestUtils.invokeMethod(paymentService, "parseLong", "bad");
        String maskedShort = ReflectionTestUtils.invokeMethod(paymentService, "maskAccountNumber", "12");
        String maskedLong = ReflectionTestUtils.invokeMethod(paymentService, "maskAccountNumber", "987654321");

        assertNotNull(randomFailure);
        assertEquals(BigDecimal.ZERO, zeroAmount);
        assertEquals(new BigDecimal("15.25"), parsedAmount);
        assertEquals(12L, parsedLong);
        assertEquals(null, invalidLong);
        assertEquals("12", maskedShort);
        assertEquals("****4321", maskedLong);
    }

    private PaymentRequest baseRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setClaimId(100L);
        request.setClaimNumber("CLM-100");
        request.setPolicyholderId(42L);
        request.setAmount(new BigDecimal("750.00"));
        request.setPaymentMethod(PaymentTransaction.PaymentMethod.NEFT);
        request.setBeneficiaryName("John Doe");
        request.setAccountNumber("123456");
        request.setIfscCode("CLSW0000123");
        request.setBankName("ClaimSwift Bank");
        request.setForceSimulateSuccess(true);
        request.setForceSimulateFailure(false);
        return request;
    }

    private Map<String, Object> approvedClaimSnapshot() {
        return Map.of(
                "status", "APPROVED",
                "approvedAmount", new BigDecimal("1000.00"),
                "policyholderId", 42L,
                "claimNumber", "CLM-100"
        );
    }

    private PaymentTransaction txnWithStatus(PaymentTransaction.PaymentStatus status, String amount) {
        return PaymentTransaction.builder()
                .status(status)
                .amount(new BigDecimal(amount))
                .build();
    }

    private void setPolicyholderOnlyAuth() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "policyholder",
                "n/a",
                Set.of(new SimpleGrantedAuthority("ROLE_POLICYHOLDER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
