package com.claimswift.notificationservice.dto;

import com.claimswift.notificationservice.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    private Long claimId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    @NotNull(message = "Notification type is required")
    private Notification.NotificationType type;

    private Notification.NotificationStatus status;
    private Boolean isRead;
    private LocalDateTime readAt;
    private String actionUrl;
    private Long senderId;
    private LocalDateTime createdAt;

    public static NotificationMessage fromEntity(Notification notification) {
        return NotificationMessage.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .claimId(notification.getClaimId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .status(notification.getStatus())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .actionUrl(notification.getActionUrl())
                .senderId(notification.getSenderId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
