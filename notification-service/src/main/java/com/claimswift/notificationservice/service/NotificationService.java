package com.claimswift.notificationservice.service;

import com.claimswift.notificationservice.dto.NotificationMessage;
import com.claimswift.notificationservice.entity.Notification;
import com.claimswift.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NotificationMessage createAndSendNotification(Long userId, Long claimId, String title, 
                                                         String message, Notification.NotificationType type,
                                                         String actionUrl, Long senderId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .claimId(claimId)
                .title(title)
                .message(message)
                .type(type)
                .status(Notification.NotificationStatus.PENDING)
                .isRead(false)
                .actionUrl(actionUrl)
                .senderId(senderId)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created for user {}: {}", userId, title);

        NotificationMessage messageDto = NotificationMessage.fromEntity(saved);
        
        sendNotificationToUser(userId, messageDto);
        
        saved.setStatus(Notification.NotificationStatus.SENT);
        notificationRepository.save(saved);

        return messageDto;
    }

    public void sendNotificationToUser(Long userId, NotificationMessage message) {
        String destination = "/topic/notifications/" + userId;
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Notification sent to user {} at {}", userId, destination);
    }

    @Transactional(readOnly = true)
    public List<NotificationMessage> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationMessage::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationMessage> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false)
                .stream()
                .map(NotificationMessage::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        int updated = notificationRepository.markAsRead(notificationId, userId, LocalDateTime.now());
        if (updated > 0) {
            log.info("Notification {} marked as read by user {}", notificationId, userId);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        log.info("{} notifications marked as read for user {}", updated, userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Notification does not belong to user: " + userId);
        }
        notificationRepository.delete(notification);
    }

    public void sendClaimStatusUpdate(Long userId, Long claimId, String claimNumber, String status) {
        String title = "Claim Status Updated";
        String message = String.format("Your claim #%s status has been updated to: %s", claimNumber, status);
        String actionUrl = "/status";

        createAndSendNotification(userId, claimId, title, message, 
            Notification.NotificationType.CLAIM_STATUS_UPDATE, actionUrl, null);
    }

    public void sendClaimApprovedNotification(Long userId, Long claimId, String claimNumber) {
        String title = "Claim Approved";
        String message = String.format("Congratulations! Your claim #%s has been approved.", claimNumber);
        String actionUrl = "/status";

        createAndSendNotification(userId, claimId, title, message, 
            Notification.NotificationType.CLAIM_APPROVED, actionUrl, null);
    }

    public void sendPaymentProcessedNotification(Long userId, Long claimId, String claimNumber, String amount) {
        String title = "Payment Processed";
        String message = String.format("Payment of %s for claim #%s has been processed.", amount, claimNumber);
        String actionUrl = "/history";

        createAndSendNotification(userId, claimId, title, message, 
            Notification.NotificationType.PAYMENT_PROCESSED, actionUrl, null);
    }
}
