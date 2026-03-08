package com.claimswift.claimservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/internal/claim-status")
    void sendClaimStatusNotification(@RequestBody Map<String, Object> payload);

    @PostMapping("/api/notifications/send")
    void sendNotification(@RequestBody Map<String, Object> payload);
}
