package com.claimswift.notificationservice.controller;

import com.claimswift.notificationservice.dto.NotificationMessage;
import com.claimswift.notificationservice.service.NotificationService;
import com.claimswift.notificationservice.util.StandardResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@Validated
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<NotificationMessage>>> getMyNotifications(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("username") String username) {
        log.info("Fetching notifications for user: {}", username);
        
        List<NotificationMessage> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(StandardResponse.success("Notifications retrieved", notifications));
    }

    @GetMapping("/unread")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<List<NotificationMessage>>> getUnreadNotifications(
            @RequestAttribute("userId") Long userId) {
        List<NotificationMessage> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(StandardResponse.success("Unread notifications retrieved", notifications));
    }

    @GetMapping("/unread/count")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<Map<String, Long>>> getUnreadCount(
            @RequestAttribute("userId") Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(StandardResponse.success("Unread count retrieved", 
            Map.of("unreadCount", count)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<Void>> markAsRead(
            @PathVariable("id") @Positive(message = "Notification ID must be positive") Long id,
            @RequestAttribute("userId") Long userId) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(StandardResponse.success("Notification marked as read", null));
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<Void>> markAllAsRead(
            @RequestAttribute("userId") Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(StandardResponse.success("All notifications marked as read", null));
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<NotificationMessage>> sendNotification(
            @Valid @RequestBody NotificationMessage request) {
        
        log.info("Sending notification to user {}", request.getUserId());
        
        NotificationMessage sent = notificationService.createAndSendNotification(
            request.getUserId(),
            request.getClaimId(),
            request.getTitle(),
            request.getMessage(),
            request.getType(),
            request.getActionUrl(),
            request.getSenderId()
        );
        
        return ResponseEntity.ok(StandardResponse.success("Notification sent", sent));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<Void>> deleteNotification(
            @PathVariable("id") @Positive(message = "Notification ID must be positive") Long id,
            @RequestAttribute("userId") Long userId) {
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok(StandardResponse.success("Notification deleted", null));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('POLICYHOLDER', 'ADJUSTER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<StandardResponse<NotificationMessage>> sendTestNotification(
            @RequestAttribute("userId") Long userId) {
        NotificationMessage sent = notificationService.createAndSendNotification(
                userId,
                null,
                "Test Notification",
                "This is a test notification",
                com.claimswift.notificationservice.entity.Notification.NotificationType.SYSTEM_MESSAGE,
                null,
                userId
        );
        return ResponseEntity.ok(StandardResponse.success("Test notification sent", sent));
    }

    @PostMapping("/internal/claim-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardResponse<Void>> sendInternalClaimStatusNotification(
            @RequestBody Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        Long claimId = toLong(payload.get("claimId"));
        String claimNumber = String.valueOf(payload.getOrDefault("claimNumber", claimId));
        String status = String.valueOf(payload.getOrDefault("status", "UPDATED"));

        if (userId == null || claimId == null) {
            return ResponseEntity.badRequest()
                    .body(StandardResponse.error("userId and claimId are required", "VALIDATION_ERROR"));
        }

        if ("APPROVED".equalsIgnoreCase(status)) {
            notificationService.sendClaimApprovedNotification(userId, claimId, claimNumber);
        } else {
            notificationService.sendClaimStatusUpdate(userId, claimId, claimNumber, status);
        }

        return ResponseEntity.ok(StandardResponse.success("Claim status notification sent", null));
    }

    @PostMapping("/internal/payment-processed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardResponse<Void>> sendInternalPaymentProcessedNotification(
            @RequestBody Map<String, Object> payload) {
        Long userId = toLong(payload.get("userId"));
        Long claimId = toLong(payload.get("claimId"));
        String claimNumber = String.valueOf(payload.getOrDefault("claimNumber", claimId));
        String amount = String.valueOf(payload.getOrDefault("amount", "0"));

        if (userId == null || claimId == null) {
            return ResponseEntity.badRequest()
                    .body(StandardResponse.error("userId and claimId are required", "VALIDATION_ERROR"));
        }

        notificationService.sendPaymentProcessedNotification(userId, claimId, claimNumber, amount);
        return ResponseEntity.ok(StandardResponse.success("Payment notification sent", null));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
