package com.claimswift.documentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Document Service - Vehicle Insurance Claims Processing Platform
 * Port: 8083
 * Schema: document_db
 * 
 * This service handles:
 * - Upload document with file validation
 * - Store file in: /uploads/{claimId}/filename
 * - Store metadata in DB (document_db schema)
 * - Download document
 * - Delete document
 * 
 * No cloud storage. No external dependency.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
