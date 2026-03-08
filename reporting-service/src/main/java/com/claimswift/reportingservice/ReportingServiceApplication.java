package com.claimswift.reportingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Reporting Service - Analytics and reporting for claims and payments
 * 
 * Features:
 * - Claim summary reports
 * - Payment reports
 * - Adjuster performance reports
 * - PDF export functionality
 * 
 * Feign integration with Claim Service and Payment Service
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}