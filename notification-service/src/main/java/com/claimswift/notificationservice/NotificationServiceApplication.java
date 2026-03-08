package com.claimswift.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Notification Service - Real-time notifications with WebSocket and STOMP
 * 
 * Features:
 * - Real-time claim status updates via WebSocket
 * - Read/unread notification tracking
 * - JWT-secured WebSocket connections
 * - User-specific notification delivery
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}