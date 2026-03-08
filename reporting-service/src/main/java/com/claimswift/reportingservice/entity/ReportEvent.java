package com.claimswift.reportingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_category", nullable = false, length = 40)
    private String eventCategory;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "claim_number", length = 100)
    private String claimNumber;

    @Column(name = "event_type", length = 120)
    private String eventType;

    @Column(name = "old_status", length = 40)
    private String oldStatus;

    @Column(name = "new_status", length = 40)
    private String newStatus;

    @Column(name = "event_timestamp")
    private LocalDateTime eventTimestamp;

    @Lob
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
