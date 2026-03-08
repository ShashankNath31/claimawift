package com.claimswift.notificationservice.service;

import com.claimswift.notificationservice.dto.NotificationMessage;
import com.claimswift.notificationservice.entity.Notification;
import com.claimswift.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createAndSendNotificationPersistsAndPublishes() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                notification.setId(1L);
            }
            return notification;
        });

        NotificationMessage result = notificationService.createAndSendNotification(
                11L,
                44L,
                "title",
                "body",
                Notification.NotificationType.SYSTEM_MESSAGE,
                "/claims",
                9L
        );

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(Notification.NotificationStatus.PENDING, result.getStatus());
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/11"), any(NotificationMessage.class));
    }

    @Test
    void getNotificationsAndUnreadCount() {
        Notification notification = Notification.builder()
                .id(2L)
                .userId(20L)
                .title("x")
                .message("y")
                .type(Notification.NotificationType.CLAIM_STATUS_UPDATE)
                .status(Notification.NotificationStatus.SENT)
                .isRead(false)
                .build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(notification));
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(20L, false)).thenReturn(List.of(notification));
        when(notificationRepository.countByUserIdAndIsRead(20L, false)).thenReturn(3L);

        List<NotificationMessage> all = notificationService.getUserNotifications(20L);
        List<NotificationMessage> unread = notificationService.getUnreadNotifications(20L);
        long unreadCount = notificationService.getUnreadCount(20L);

        assertEquals(1, all.size());
        assertEquals(1, unread.size());
        assertEquals(3L, unreadCount);
    }

    @Test
    void markAsReadAndMarkAllAsReadInvokeRepository() {
        when(notificationRepository.markAsRead(anyLong(), anyLong(), any())).thenReturn(1);
        when(notificationRepository.markAllAsRead(anyLong(), any())).thenReturn(4);

        notificationService.markAsRead(100L, 10L);
        notificationService.markAllAsRead(10L);

        verify(notificationRepository).markAsRead(eq(100L), eq(10L), any());
        verify(notificationRepository).markAllAsRead(eq(10L), any());
    }

    @Test
    void deleteNotificationValidatesOwnership() {
        Notification notification = Notification.builder()
                .id(6L)
                .userId(55L)
                .title("a")
                .message("b")
                .type(Notification.NotificationType.REMINDER)
                .status(Notification.NotificationStatus.SENT)
                .isRead(false)
                .build();

        when(notificationRepository.findById(6L)).thenReturn(Optional.of(notification));

        RuntimeException mismatch = assertThrows(
                RuntimeException.class,
                () -> notificationService.deleteNotification(6L, 99L)
        );
        assertNotNull(mismatch.getMessage());

        notificationService.deleteNotification(6L, 55L);
        verify(notificationRepository).delete(notification);
    }

    @Test
    void convenienceSendMethodsBuildExpectedPayloads() {
        NotificationService spyService = spy(new NotificationService(notificationRepository, messagingTemplate));
        doReturn(NotificationMessage.builder().id(1L).build())
                .when(spyService)
                .createAndSendNotification(anyLong(), anyLong(), any(), any(), any(), any(), any());

        spyService.sendClaimStatusUpdate(1L, 10L, "CLM-10", "UNDER_REVIEW");
        spyService.sendClaimApprovedNotification(2L, 11L, "CLM-11");
        spyService.sendPaymentProcessedNotification(3L, 12L, "CLM-12", "5000");

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(spyService, times(3)).createAndSendNotification(
                anyLong(),
                anyLong(),
                titleCaptor.capture(),
                any(),
                any(),
                any(),
                isNull()
        );

        List<String> titles = titleCaptor.getAllValues();
        assertEquals("Claim Status Updated", titles.get(0));
        assertEquals("Claim Approved", titles.get(1));
        assertEquals("Payment Processed", titles.get(2));
    }
}
