package com.claimswift.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Payment Service - Simulated Payment Gateway (Sandbox)
 * 
 * IMPORTANT: This is a SIMULATED Payment Gateway for testing purposes only.
 * No real money is processed. All transactions are simulated.
 * 
 * This service handles:
 * - Simulated payment processing
 * - Payment approval/rejection simulation
 * - Transaction logging
 * - Claim status updates via Feign
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}